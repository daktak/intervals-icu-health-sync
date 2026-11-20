package icu.intervals.healthsync.sync

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Pure scheduling logic for the daily auto-sync.
 *
 * The sync window itself ([SyncService.resolveSyncDates]) already includes
 * today; the gap was *when* the single daily worker fires. These helpers
 * compute the delay until the next configured local slot so the worker runs
 * in the morning, after last night's sleep and morning metrics are available.
 */
object SyncSchedule {

    const val DEFAULT_MINUTE_OF_DAY = 8 * 60 // 08:00 local
    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * Millis until the next occurrence of [minuteOfDay] (0..[MINUTES_PER_DAY)
     * exclusive) in the zone of [now]. Never negative.
     */
    fun nextDelayMillis(now: ZonedDateTime, minuteOfDay: Int): Long {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minuteOfDay must be 0..${MINUTES_PER_DAY - 1}" }
        val target = localAtZone(now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60), now.zone)
        val next = if (now.isBefore(target)) target else localAtZone(now.toLocalDate().plusDays(1).atTime(minuteOfDay / 60, minuteOfDay % 60), now.zone)
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }

    /** Earliest time today counts as "synced after the morning slot" (slot + grace). */
    fun deadlineAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId, graceMinutes: Long): ZonedDateTime =
        localAtZone(date.atTime(minuteOfDay / 60, minuteOfDay % 60), zone).plusMinutes(graceMinutes)

    /** Formats [minuteOfDay] as HH:mm. */
    fun formatMinuteOfDay(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /**
     * Parses an HH:mm input into minutes since midnight (0..[MINUTES_PER_DAY)).
     * Returns null for empty or invalid input (lenient about one/two digits and
     * optional seconds).
     */
    fun parseMinuteOfDay(value: String?): Int? {
        val input = value?.trim() ?: return null
        if (input.isEmpty()) return null

        val formatters = listOf(
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ISO_LOCAL_TIME,
        )
        val time = formatters.mapNotNull { formatter ->
            try {
                LocalTime.parse(input, formatter)
            } catch (_: Exception) {
                null
            }
        }.firstOrNull() ?: return null

        val minutes = time.hour * 60 + time.minute
        return if (minutes in 0 until MINUTES_PER_DAY) minutes else null
    }

    /**
     * Resolves a local date-time to a zone instant. Avoids
     * [LocalDateTime.atZone] throwing on nonexistent times (spring-forward
     * gaps) by reusing the offset that is currently in effect.
     */
    private fun localAtZone(localDateTime: LocalDateTime, zone: ZoneId): ZonedDateTime =
        try {
            localDateTime.atZone(zone)
        } catch (_: Exception) {
            val offset = zone.getRules().getOffset(Instant.now())
            ZonedDateTime.ofInstant(Instant.ofEpochSecond(localDateTime.toEpochSecond(offset)), zone)
        }
}
