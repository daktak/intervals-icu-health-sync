package icu.intervals.healthsync.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import icu.intervals.healthsync.model.DayStats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectReader(context: Context) {

    private val client = HealthConnectClient.getOrCreate(context)

    suspend fun readDays(dates: Collection<LocalDate>): Map<LocalDate, DayStats> {
        val zone = ZoneId.systemDefault()
        val requested = dates.toSortedSet()
        if (requested.isEmpty()) return emptyMap()

        val windowStart = requested.first().atStartOfDay(zone).toInstant()
        val windowEnd = requested.last().plusDays(1).atStartOfDay(zone).toInstant()
        val window = TimeRangeFilter.between(windowStart, windowEnd)
        val builders = requested.associateWith { DayBuilder(it) }

        suspend fun bucket(time: Instant): DayBuilder? =
            builders[time.atZone(zone).toLocalDate()]

        safeRead {
            for (record in readAll<WeightRecord>(window)) {
                val builder = bucket(record.time) ?: continue
                if (builder.isNewer(KEY_WEIGHT, record.time)) {
                    builder.weightKg = record.weight.inKilograms
                }
            }
            for (record in readAll<RestingHeartRateRecord>(window)) {
                val builder = bucket(record.time) ?: continue
                if (builder.isNewer(KEY_RESTING_HR, record.time)) {
                    builder.restingHr = record.beatsPerMinute
                }
            }
            for (record in readAll<HeartRateVariabilityRmssdRecord>(window)) {
                val builder = bucket(record.time) ?: continue
                if (builder.isNewer(KEY_HRV, record.time)) {
                    builder.hrvRmssdMs = record.heartRateVariabilityMillis
                }
            }
            for (record in readAll<Vo2MaxRecord>(window)) {
                val builder = bucket(record.time) ?: continue
                if (builder.isNewer(KEY_VO2, record.time)) {
                    builder.vo2Max = record.vo2MillilitersPerMinuteKilogram
                }
            }
            for (record in readAll<BloodPressureRecord>(window)) {
                val builder = bucket(record.time) ?: continue
                if (builder.isNewer(KEY_BLOOD_PRESSURE, record.time)) {
                    builder.systolicMmHg = record.systolic.inMillimetersOfMercury
                    builder.diastolicMmHg = record.diastolic.inMillimetersOfMercury
                }
            }
            for (record in readAll<BloodGlucoseRecord>(window)) {
                val builder = bucket(record.time) ?: continue
                if (builder.isNewer(KEY_GLUCOSE, record.time)) {
                    builder.bloodGlucoseMmol = record.level.inMillimolesPerLiter
                }
            }
            for (record in readAll<BodyFatRecord>(window)) {
                val builder = bucket(record.time) ?: continue
                if (builder.isNewer(KEY_BODY_FAT, record.time)) {
                    builder.bodyFatPercent = record.percentage.value
                }
            }
        }

        safeRead {
            for (record in readAll<OxygenSaturationRecord>(window)) {
                bucket(record.time)?.addSpO2(record.percentage.value)
            }
            for (record in readAll<RespiratoryRateRecord>(window)) {
                bucket(record.time)?.addRespiration(record.rate)
            }
        }

        val mainSleepSessions = mutableListOf<Pair<DayBuilder, SleepSessionRecord>>()
        safeRead {
            for (record in readAll<SleepSessionRecord>(window)) {
                val builder = builders[record.endTime.atZone(zone).toLocalDate()] ?: continue
                if (!isMainSleep(record, zone)) continue
                val seconds = Duration.between(record.startTime, record.endTime).seconds
                if (seconds <= 0 || seconds > MAX_SLEEP_SECONDS) continue
                builder.addSleepSeconds(seconds)
                mainSleepSessions += builder to record
            }
        }

        safeRead {
            for ((builder, session) in mainSleepSessions) {
                val aggregate = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.MEASUREMENTS_COUNT),
                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                    )
                )
                val bpmAvg = aggregate[HeartRateRecord.BPM_AVG]
                val count = aggregate[HeartRateRecord.MEASUREMENTS_COUNT]
                if (bpmAvg != null && count != null) {
                    builder.addSleepHeartRate(bpmAvg, count)
                }
            }
        }

        safeRead {
            for (record in readAll<HydrationRecord>(window)) {
                val builder = builders[record.startTime.atZone(zone).toLocalDate()] ?: continue
                builder.addHydrationLitres(record.volume.inLiters)
            }
        }

        safeRead {
            for (record in readAll<NutritionRecord>(window)) {
                val builder = builders[record.startTime.atZone(zone).toLocalDate()] ?: continue
                builder.addNutrition(
                    kcal = record.energy?.inKilocalories,
                    carbsGrams = record.totalCarbohydrate?.inGrams,
                    proteinGrams = record.protein?.inGrams,
                    fatGrams = record.totalFat?.inGrams,
                )
            }
        }

        for ((date, builder) in builders) {
            safeRead { builder.steps = stepsForDay(date, zone) }
        }

        return builders.values.associateBy { it.date }.mapValues { it.value.toDayStats() }
    }

    private suspend fun stepsForDay(date: LocalDate, zone: ZoneId): Long? {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val aggregate = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )
        return aggregate[StepsRecord.COUNT_TOTAL]
    }

    private suspend fun safeRead(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: SecurityException) {
        }
    }

    private suspend inline fun <reified T : Record> readAll(window: TimeRangeFilter): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = window,
                    pageToken = pageToken,
                )
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    private fun isMainSleep(session: SleepSessionRecord, zone: ZoneId): Boolean {
        val start = session.startTime.atZone(zone)
        val end = session.endTime.atZone(zone)
        return end.hour < 12 || start.hour >= 18
    }

    private class DayBuilder(val date: LocalDate) {
        private val latestAt = HashMap<String, Instant>()

        var weightKg: Double? = null
        var restingHr: Long? = null
        var hrvRmssdMs: Double? = null
        var vo2Max: Double? = null
        var systolicMmHg: Double? = null
        var diastolicMmHg: Double? = null
        var bloodGlucoseMmol: Double? = null
        var bodyFatPercent: Double? = null
        var steps: Long? = null
        var sleepSeconds: Long? = null
        var hydrationLitres: Double? = null
        var kcalSum: Double = 0.0
        var carbsSum: Double = 0.0
        var proteinSum: Double = 0.0
        var fatSum: Double = 0.0
        var nutritionRecords: Int = 0

        private var spO2Sum = 0.0
        private var spO2Count = 0
        private var respirationSum = 0.0
        private var respirationCount = 0
        private var sleepHrWeightedSum = 0.0
        private var sleepHrSamples = 0L

        fun isNewer(key: String, time: Instant): Boolean {
            val previous = latestAt[key]
            if (previous != null && previous >= time) return false
            latestAt[key] = time
            return true
        }

        fun addSpO2(value: Double) {
            spO2Sum += value
            spO2Count++
        }

        fun addRespiration(value: Double) {
            respirationSum += value
            respirationCount++
        }

        fun addSleepSeconds(seconds: Long) {
            sleepSeconds = (sleepSeconds ?: 0L) + seconds
        }

        fun addSleepHeartRate(bpmAverage: Long, sampleCount: Long) {
            if (sampleCount <= 0) return
            sleepHrWeightedSum += bpmAverage.toDouble() * sampleCount
            sleepHrSamples += sampleCount
        }

        fun addHydrationLitres(litres: Double) {
            hydrationLitres = (hydrationLitres ?: 0.0) + litres
        }

        fun addNutrition(kcal: Double?, carbsGrams: Double?, proteinGrams: Double?, fatGrams: Double?) {
            nutritionRecords++
            kcal?.let { kcalSum += it }
            carbsGrams?.let { carbsSum += it }
            proteinGrams?.let { proteinSum += it }
            fatGrams?.let { fatSum += it }
        }

        fun toDayStats(): DayStats = DayStats(
            date = date,
            weightKg = weightKg,
            restingHr = restingHr,
            hrvRmssdMs = hrvRmssdMs,
            sleepSecs = sleepSeconds,
            avgSleepingHr = if (sleepHrSamples > 0) sleepHrWeightedSum / sleepHrSamples else null,
            steps = steps,
            spO2Percent = if (spO2Count > 0) spO2Sum / spO2Count else null,
            bodyFatPercent = bodyFatPercent,
            vo2Max = vo2Max,
            systolicMmHg = systolicMmHg,
            diastolicMmHg = diastolicMmHg,
            bloodGlucoseMmol = bloodGlucoseMmol,
            respirationPerMin = if (respirationCount > 0) respirationSum / respirationCount else null,
            hydrationLitres = hydrationLitres,
            kcalConsumed = if (nutritionRecords > 0) Math.round(kcalSum).toInt() else null,
            carbsGrams = if (nutritionRecords > 0) carbsSum else null,
            proteinGrams = if (nutritionRecords > 0) proteinSum else null,
            fatGrams = if (nutritionRecords > 0) fatSum else null,
        )
    }

    private companion object {
        const val KEY_WEIGHT = "weight"
        const val KEY_RESTING_HR = "resting_hr"
        const val KEY_HRV = "hrv"
        const val KEY_VO2 = "vo2"
        const val KEY_BLOOD_PRESSURE = "blood_pressure"
        const val KEY_GLUCOSE = "glucose"
        const val KEY_BODY_FAT = "body_fat"
        const val MAX_SLEEP_SECONDS = 18 * 60 * 60L
    }
}
