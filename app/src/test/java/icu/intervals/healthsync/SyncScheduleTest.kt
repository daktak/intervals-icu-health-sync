package icu.intervals.healthsync

import icu.intervals.healthsync.sync.SyncSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class SyncScheduleTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun zoned(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone)

    @Test
    fun beforeSlotReturnsDelayToToday() {
        val now = zoned(2026, 10, 8, 7, 0)
        val delay = SyncSchedule.nextDelayMillis(now, 8 * 60)

        val expected = now.toLocalDate().atTime(8, 0).atZone(zone).toInstant().toEpochMilli() -
            now.toInstant().toEpochMilli()
        assertEquals(expected, delay)
    }

    @Test
    fun afterSlotReturnsDelayToTomorrow() {
        val now = zoned(2026, 10, 8, 9, 0)
        val delay = SyncSchedule.nextDelayMillis(now, 8 * 60)

        val expected = now.toLocalDate().plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli() -
            now.toInstant().toEpochMilli()
        assertEquals(expected, delay)
    }

    @Test
    fun nonDefaultSlot() {
        val now = zoned(2026, 10, 8, 6, 0)
        val delay = SyncSchedule.nextDelayMillis(now, 6 * 60 + 45) // 06:45

        val expected = now.toLocalDate().atTime(6, 45).atZone(zone).toInstant().toEpochMilli() -
            now.toInstant().toEpochMilli()
        assertEquals(expected, delay)
    }

    @Test
    fun delayIsAlwaysPositiveAndLessThanADay() {
        val now = zoned(2026, 10, 8, 12, 30)
        val delay = SyncSchedule.nextDelayMillis(now, 3 * 60) // 03:00

        assertTrue(delay > 0)
        assertTrue(delay < 24 * 60 * 60 * 1000)
    }

    @Test
    fun deadlineIncludesGracePeriod() {
        val date = LocalDate.of(2026, 10, 8)
        val deadline = SyncSchedule.deadlineAt(date, 8 * 60, zone, 60)

        assertEquals(LocalDateTime.of(2026, 10, 8, 9, 0), deadline.toLocalDateTime())
    }

    @Test
    fun parseMinuteOfDayAcceptsValidTimes() {
        assertEquals(8 * 60, SyncSchedule.parseMinuteOfDay("08:00"))
        assertEquals(0, SyncSchedule.parseMinuteOfDay("00:00"))
        assertEquals(23 * 60 + 59, SyncSchedule.parseMinuteOfDay("23:59"))
        assertEquals(6 * 60 + 45, SyncSchedule.parseMinuteOfDay("6:45"))
        assertEquals(14 * 60, SyncSchedule.parseMinuteOfDay("14:00:00"))
    }

    @Test
    fun parseMinuteOfDayRejectsInvalidTimes() {
        assertNull(SyncSchedule.parseMinuteOfDay(null))
        assertNull(SyncSchedule.parseMinuteOfDay(""))
        assertNull(SyncSchedule.parseMinuteOfDay("  "))
        assertNull(SyncSchedule.parseMinuteOfDay("25:00"))
        assertNull(SyncSchedule.parseMinuteOfDay("08:60"))
        assertNull(SyncSchedule.parseMinuteOfDay("not-a-time"))
        assertNull(SyncSchedule.parseMinuteOfDay("08:0"))
        assertNull(SyncSchedule.parseMinuteOfDay("8:00:99"))
    }
}
