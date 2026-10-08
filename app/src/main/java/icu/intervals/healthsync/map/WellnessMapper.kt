package icu.intervals.healthsync.map

import icu.intervals.healthsync.model.DayStats
import icu.intervals.healthsync.model.MetricGroup
import icu.intervals.healthsync.model.WellnessEntry
import kotlin.math.round

object WellnessMapper {

    fun map(days: Collection<DayStats>, enabledGroups: Set<MetricGroup>): List<WellnessEntry> =
        days.filter { it.hasAnyValue() }
            .sortedBy { it.date }
            .mapNotNull { mapDay(it, enabledGroups) }

    private fun mapDay(day: DayStats, enabledGroups: Set<MetricGroup>): WellnessEntry? {
        val core = MetricGroup.CORE in enabledGroups
        val body = MetricGroup.BODY_COMPOSITION in enabledGroups
        val vitals = MetricGroup.VITALS in enabledGroups
        val nutrition = MetricGroup.NUTRITION in enabledGroups

        val entry = WellnessEntry(
            id = day.date.toString(),
            weight = if (core) day.weightKg?.roundTo(1) else null,
            restingHR = if (core) day.restingHr else null,
            hrv = if (core) day.hrvRmssdMs?.roundTo(1) else null,
            sleepSecs = if (core) day.sleepSecs else null,
            avgSleepingHR = if (core) day.avgSleepingHr?.roundTo(0)?.toLong() else null,
            steps = if (core) day.steps else null,
            spO2 = if (core) day.spO2Percent?.roundTo(1) else null,
            bodyFat = if (body) day.bodyFatPercent?.roundTo(1) else null,
            vo2max = if (body) day.vo2Max?.roundTo(1) else null,
            systolic = if (vitals) day.systolicMmHg?.roundTo(0)?.toLong() else null,
            diastolic = if (vitals) day.diastolicMmHg?.roundTo(0)?.toLong() else null,
            bloodGlucose = if (vitals) day.bloodGlucoseMmol?.roundTo(2) else null,
            respiration = if (vitals) day.respirationPerMin?.roundTo(1) else null,
            hydrationVolume = if (nutrition) day.hydrationLitres?.roundTo(2) else null,
            kcalConsumed = if (nutrition) day.kcalConsumed else null,
            carbohydrates = if (nutrition) day.carbsGrams?.roundTo(1) else null,
            protein = if (nutrition) day.proteinGrams?.roundTo(1) else null,
            fatTotal = if (nutrition) day.fatGrams?.roundTo(1) else null,
        )
        return if (entry.hasData()) entry else null
    }

    private fun Double.roundTo(decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return round(this * factor) / factor
    }
}
