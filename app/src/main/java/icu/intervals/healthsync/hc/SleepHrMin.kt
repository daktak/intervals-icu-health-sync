package icu.intervals.healthsync.hc

import java.time.Instant
import java.time.temporal.ChronoUnit

/** A single raw heart-rate sample. */
data class HrSample(val time: Instant, val bpm: Long)

/**
 * Returns the lowest 1-minute bucket average BPM among samples falling within
 * [start, end], or null when there are none.
 *
 * Samples are grouped into 1-minute buckets (truncated to the minute); each
 * bucket's average BPM is computed, and the minimum bucket average is returned
 * (rounded to the nearest whole BPM). This filters out brief single-sample
 * sensor dips that a raw minimum would otherwise report.
 *
 * Kept separate from [HealthConnectReader] so the logic can be unit-tested on
 * the JVM without a Health Connect client.
 */
object SleepHrMin {
    fun minInWindow(samples: List<HrSample>, start: Instant, end: Instant): Long? {
        val buckets = samples
            .filter { !it.time.isBefore(start) && !it.time.isAfter(end) && it.bpm > 0 }
            .groupBy { it.time.truncatedTo(ChronoUnit.MINUTES) }
        if (buckets.isEmpty()) return null
        val lowest = buckets.values
            .map { bucket -> bucket.map { it.bpm }.average() }
            .minOrNull() ?: return null
        return Math.round(lowest)
    }
}