package icu.intervals.healthsync.hc

import icu.intervals.healthsync.hc.SleepHrMin
import icu.intervals.healthsync.hc.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SleepHrMinTest {

    private val start = Instant.parse("2026-10-07T22:00:00Z")
    private val end = Instant.parse("2026-10-08T07:00:00Z")

    @Test
    fun emptyListReturnsNull() {
        assertNull(SleepHrMin.minInWindow(emptyList(), start, end))
    }

    @Test
    fun samplesOutsideWindowIgnored() {
        val samples = listOf(
            HrSample(start.minus(1, ChronoUnit.HOURS), 40L),
            HrSample(end.plus(1, ChronoUnit.HOURS), 41L),
            HrSample(start.plus(30, ChronoUnit.MINUTES), 55L),
        )
        assertEquals(55L, SleepHrMin.minInWindow(samples, start, end))
    }

    @Test
    fun lowestValueWins() {
        val samples = listOf(
            HrSample(start.plus(1, ChronoUnit.HOURS), 55L),
            HrSample(start.plus(2, ChronoUnit.HOURS), 45L),
            HrSample(start.plus(3, ChronoUnit.HOURS), 50L),
        )
        assertEquals(45L, SleepHrMin.minInWindow(samples, start, end))
    }

    @Test
    fun nonPositiveValuesIgnored() {
        val samples = listOf(
            HrSample(start.plus(1, ChronoUnit.HOURS), 0L),
            HrSample(start.plus(2, ChronoUnit.HOURS), -3L),
            HrSample(start.plus(3, ChronoUnit.HOURS), 48L),
        )
        assertEquals(48L, SleepHrMin.minInWindow(samples, start, end))
    }

    @Test
    fun allNonPositiveReturnsNull() {
        val samples = listOf(
            HrSample(start.plus(1, ChronoUnit.HOURS), 0L),
            HrSample(start.plus(2, ChronoUnit.HOURS), -1L),
        )
        assertNull(SleepHrMin.minInWindow(samples, start, end))
    }

    @Test
    fun boundariesAreInclusive() {
        val samples = listOf(
            HrSample(start, 44L),
            HrSample(end, 46L),
        )
        assertEquals(44L, SleepHrMin.minInWindow(samples, start, end))
    }
}
