package icu.intervals.healthsync.model

import org.json.JSONObject

data class WellnessEntry(
    val id: String,
    val weight: Double? = null,
    val restingHR: Long? = null,
    val hrv: Double? = null,
    val sleepSecs: Long? = null,
    val avgSleepingHR: Long? = null,
    val steps: Long? = null,
    val spO2: Double? = null,
    val bodyFat: Double? = null,
    val vo2max: Double? = null,
    val systolic: Long? = null,
    val diastolic: Long? = null,
    val bloodGlucose: Double? = null,
    val respiration: Double? = null,
    val hydrationVolume: Double? = null,
    val kcalConsumed: Int? = null,
    val carbohydrates: Double? = null,
    val protein: Double? = null,
    val fatTotal: Double? = null,
) {
    fun hasData(): Boolean =
        weight != null || restingHR != null || hrv != null || sleepSecs != null ||
            avgSleepingHR != null || steps != null || spO2 != null || bodyFat != null ||
            vo2max != null || systolic != null || diastolic != null || bloodGlucose != null ||
            respiration != null || hydrationVolume != null || kcalConsumed != null ||
            carbohydrates != null || protein != null || fatTotal != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        weight?.let { put("weight", it) }
        restingHR?.let { put("restingHR", it) }
        hrv?.let { put("hrv", it) }
        sleepSecs?.let { put("sleepSecs", it) }
        avgSleepingHR?.let { put("avgSleepingHR", it) }
        steps?.let { put("steps", it) }
        spO2?.let { put("spO2", it) }
        bodyFat?.let { put("bodyFat", it) }
        vo2max?.let { put("vo2max", it) }
        systolic?.let { put("systolic", it) }
        diastolic?.let { put("diastolic", it) }
        bloodGlucose?.let { put("bloodGlucose", it) }
        respiration?.let { put("respiration", it) }
        hydrationVolume?.let { put("hydrationVolume", it) }
        kcalConsumed?.let { put("kcalConsumed", it) }
        carbohydrates?.let { put("carbohydrates", it) }
        protein?.let { put("protein", it) }
        fatTotal?.let { put("fatTotal", it) }
    }
}
