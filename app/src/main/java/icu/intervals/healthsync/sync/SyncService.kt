package icu.intervals.healthsync.sync

import android.content.Context
import icu.intervals.healthsync.hc.HealthConnectReader
import icu.intervals.healthsync.icu.IntervalsClient
import icu.intervals.healthsync.icu.UploadResult
import icu.intervals.healthsync.map.WellnessMapper
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SyncService(private val context: Context) {

    data class Outcome(
        val success: Boolean,
        val message: String,
        val retryable: Boolean = false,
    )

    suspend fun sync(daysBack: Int? = null): Outcome {
        val prefs = AppPreferences(context)
        val apiKey = prefs.apiKey
        if (apiKey.isBlank()) {
            return remember(prefs, Outcome(false, "Enter your intervals.icu API key first"))
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dates = resolveSyncDates(prefs.lastSyncMillis, daysBack, today, zone)

        return try {
            val days = HealthConnectReader(context).readDays(dates)
            val entries = WellnessMapper.map(days.values, prefs.enabledGroups())
            if (entries.isEmpty()) {
                prefs.lastSyncMillis = System.currentTimeMillis()
                remember(
                    prefs,
                    Outcome(true, "No Health Connect data in the last ${dates.size} day(s)"),
                )
            } else {
                when (val result = IntervalsClient(apiKey).putWellnessBulk(entries)) {
                    is UploadResult.Success -> {
                        prefs.lastSyncMillis = System.currentTimeMillis()
                        remember(prefs, Outcome(true, "Uploaded ${result.dayCount} day(s) to intervals.icu"))
                    }
                    is UploadResult.Failure -> {
                        val retryable = result.code == 429 || result.code >= 500
                        remember(
                            prefs,
                            Outcome(
                                false,
                                "intervals.icu error ${result.code}: ${result.body}",
                                retryable = retryable,
                            ),
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            remember(
                prefs,
                Outcome(
                    false,
                    "Health Connect permission missing - tap \"Grant Health Connect access\"",
                ),
            )
        } catch (e: IOException) {
            remember(prefs, Outcome(false, "Network error: ${e.message}", retryable = true))
        } catch (e: Exception) {
            remember(prefs, Outcome(false, "Sync failed: ${e.message ?: e}"))
        }
    }

    private fun remember(prefs: AppPreferences, outcome: Outcome): Outcome {
        prefs.lastResult = outcome.message
        return outcome
    }

    companion object {
        const val AUTO_BACKFILL_DAYS = 30L
        const val MAX_WINDOW_DAYS = 60L

        fun resolveSyncDates(
            lastSyncMillis: Long,
            daysBack: Int?,
            today: LocalDate,
            zone: ZoneId,
        ): List<LocalDate> {
            if (daysBack != null) {
                val count = daysBack.coerceIn(1, 365).toLong()
                return (count - 1 downTo 0L).map { today.minusDays(it) }
            }
            val fromDate = if (lastSyncMillis <= 0L) {
                today.minusDays(AUTO_BACKFILL_DAYS)
            } else {
                Instant.ofEpochMilli(lastSyncMillis).atZone(zone).toLocalDate().minusDays(1)
            }
            val floor = today.minusDays(MAX_WINDOW_DAYS - 1)
            val start = if (fromDate.isBefore(floor)) floor else fromDate
            return generateSequence(start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(today) }
                .toList()
        }
    }
}
