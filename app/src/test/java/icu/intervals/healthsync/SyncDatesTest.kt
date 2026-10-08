package icu.intervals.healthsync

import icu.intervals.healthsync.sync.SyncService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SyncDatesTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun firstRunBackfillsThirtyDaysIncludingToday() {
        val dates = SyncService.resolveSyncDates(0L, null, today, zone)

        assertEquals(31, dates.size)
        assertEquals(LocalDate.of(2026, 9, 8), dates.first())
        assertEquals(today, dates.last())
    }

    @Test
    fun incrementalRunStartsDayBeforeLastSync() {
        val lastSync = today.minusDays(2)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val dates = SyncService.resolveSyncDates(lastSync, null, today, zone)

        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7),
                today,
            ),
            dates,
        )
    }

    @Test
    fun windowIsCappedAtSixtyDays() {
        val lastSync = today.minusDays(400)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val dates = SyncService.resolveSyncDates(lastSync, null, today, zone)

        assertEquals(60, dates.size)
        assertEquals(today.minusDays(59), dates.first())
        assertEquals(today, dates.last())
    }

    @Test
    fun explicitDaysBackCountsBackwardsFromToday() {
        val dates = SyncService.resolveSyncDates(0L, 3, today, zone)

        assertEquals(
            listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7), today),
            dates,
        )
    }

    @Test
    fun explicitDaysBackIsClamped() {
        val dates = SyncService.resolveSyncDates(0L, 0, today, zone)

        assertEquals(listOf(today), dates)
        assertTrue(SyncService.resolveSyncDates(0L, 10_000, today, zone).size <= 365)
    }
}
