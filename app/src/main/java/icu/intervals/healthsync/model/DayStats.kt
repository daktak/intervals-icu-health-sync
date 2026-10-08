package icu.intervals.healthsync.model

import java.time.LocalDate

data class DayStats(
    val date: LocalDate,
    val weightKg: Double? = null,
    val restingHr: Long? = null,
    val hrvRmssdMs: Double? = null,
    val sleepSecs: Long? = null,
    val avgSleepingHr: Double? = null,
    val steps: Long? = null,
    val spO2Percent: Double? = null,
    val bodyFatPercent: Double? = null,
    val vo2Max: Double? = null,
    val systolicMmHg: Double? = null,
    val diastolicMmHg: Double? = null,
    val bloodGlucoseMmol: Double? = null,
    val respirationPerMin: Double? = null,
    val hydrationLitres: Double? = null,
    val kcalConsumed: Int? = null,
    val carbsGrams: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
) {
    fun hasAnyValue(): Boolean =
        weightKg != null || restingHr != null || hrvRmssdMs != null || sleepSecs != null ||
            avgSleepingHr != null || steps != null || spO2Percent != null || bodyFatPercent != null ||
            vo2Max != null || systolicMmHg != null || diastolicMmHg != null ||
            bloodGlucoseMmol != null || respirationPerMin != null || hydrationLitres != null ||
            kcalConsumed != null || carbsGrams != null || proteinGrams != null || fatGrams != null
}
