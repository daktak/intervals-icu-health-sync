package icu.intervals.healthsync.hc

import java.time.Instant

/** A single raw heart-rate sample. */
data class HrSample(val time: Instant, val bpm: Long)

/**
 * Returns the lowest positive BPM among samples falling within [start, end],
 * or null when there are none.
 *
 * Kept separate from [HealthConnectReader] so the min-selection logic can be
 * unit-tested on the JVM without a Health Connect client.
 */
object SleepHrMin {
    fun minInWindow(samples: List<HrSample>, start: Instant, end: Instant): Long? =
        samples.filter { !it.time.isBefore(start) && !it.time.isAfter(end) && it.bpm > 0 }
            .minOfOrNull { it.bpm }
}
