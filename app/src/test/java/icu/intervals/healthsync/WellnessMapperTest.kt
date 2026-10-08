package icu.intervals.healthsync

import icu.intervals.healthsync.map.WellnessMapper
import icu.intervals.healthsync.model.DayStats
import icu.intervals.healthsync.model.MetricGroup
import icu.intervals.healthsync.model.WellnessEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WellnessMapperTest {

    private val date = LocalDate.of(2026, 10, 8)
    private val allGroups = MetricGroup.entries.toSet()

    @Test
    fun mapsCoreFieldsWithRounding() {
        val day = DayStats(
            date = date,
            weightKg = 70.126,
            restingHr = 48,
            hrvRmssdMs = 42.44,
            sleepSecs = 25_200L,
            avgSleepingHr = 51.6,
            steps = 8_123L,
            spO2Percent = 96.66,
        )

        val entries = WellnessMapper.map(listOf(day), allGroups)

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("2026-10-08", entry.id)
        assertEquals(70.1, entry.weight!!, 0.0)
        assertEquals(48L, entry.restingHR!!)
        assertEquals(42.4, entry.hrv!!, 0.0)
        assertEquals(25_200L, entry.sleepSecs!!)
        assertEquals(52L, entry.avgSleepingHR!!)
        assertEquals(8_123L, entry.steps!!)
        assertEquals(96.7, entry.spO2!!, 0.0)
    }

    @Test
    fun mapsVitalsBodyAndNutrition() {
        val day = DayStats(
            date = date,
            bodyFatPercent = 17.44,
            vo2Max = 52.36,
            systolicMmHg = 118.4,
            diastolicMmHg = 76.2,
            bloodGlucoseMmol = 5.678,
            respirationPerMin = 14.44,
            hydrationLitres = 1.234,
            kcalConsumed = 2_450,
            carbsGrams = 250.55,
            proteinGrams = 140.1,
            fatGrams = 70.04,
        )

        val entry = WellnessMapper.map(listOf(day), allGroups).single()

        assertEquals(17.4, entry.bodyFat!!, 0.0)
        assertEquals(52.4, entry.vo2max!!, 0.0)
        assertEquals(118L, entry.systolic!!)
        assertEquals(76L, entry.diastolic!!)
        assertEquals(5.68, entry.bloodGlucose!!, 0.0)
        assertEquals(14.4, entry.respiration!!, 0.0)
        assertEquals(1.23, entry.hydrationVolume!!, 0.0)
        assertEquals(2_450, entry.kcalConsumed!!)
        assertEquals(250.6, entry.carbohydrates!!, 0.0)
        assertEquals(140.1, entry.protein!!, 0.0)
        assertEquals(70.0, entry.fatTotal!!, 0.0)
    }

    @Test
    fun skipsDisabledGroups() {
        val day = DayStats(
            date = date,
            weightKg = 70.0,
            bodyFatPercent = 18.0,
            systolicMmHg = 120.0,
            hydrationLitres = 2.0,
        )

        val entry = WellnessMapper.map(listOf(day), setOf(MetricGroup.CORE)).single()

        assertEquals(70.0, entry.weight!!, 0.0)
        assertNull(entry.bodyFat)
        assertNull(entry.systolic)
        assertNull(entry.hydrationVolume)
        assertTrue(entry.hasData())
    }

    @Test
    fun returnsNothingWhenGroupsExcludeAllFields() {
        val day = DayStats(
            date = date,
            bodyFatPercent = 18.0,
            systolicMmHg = 120.0,
        )

        val entries = WellnessMapper.map(listOf(day), setOf(MetricGroup.CORE))

        assertTrue(entries.isEmpty())
    }

    @Test
    fun skipsDaysWithoutAnyValue() {
        val empty = DayStats(date = date)
        val withData = DayStats(date = date.minusDays(1), restingHr = 50L)

        val entries = WellnessMapper.map(listOf(empty, withData), allGroups)

        assertEquals(1, entries.size)
        assertEquals("2026-10-07", entries.single().id)
    }

    @Test
    fun sortsEntriesByDate() {
        val newer = DayStats(date = date, restingHr = 50L)
        val older = DayStats(date = date.minusDays(3), restingHr = 51L)

        val entries = WellnessMapper.map(listOf(newer, older), allGroups)

        assertEquals(listOf("2026-10-05", "2026-10-08"), entries.map { it.id })
    }

    @Test
    fun jsonContainsOnlyPopulatedFields() {
        val entry = WellnessEntry(id = "2026-10-08", weight = 70.0, restingHR = 48L)

        val json: JSONObject = entry.toJson()

        assertEquals("2026-10-08", json.getString("id"))
        assertEquals(70.0, json.getDouble("weight"), 0.0)
        assertEquals(48, json.getInt("restingHR"))
        assertFalse(json.has("hrv"))
        assertFalse(json.has("sleepSecs"))
        assertFalse(json.has("steps"))
        assertFalse(json.has("bloodGlucose"))
    }
}
