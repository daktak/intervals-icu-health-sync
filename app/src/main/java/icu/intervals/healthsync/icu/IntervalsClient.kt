package icu.intervals.healthsync.icu

import icu.intervals.healthsync.model.WellnessEntry
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

sealed class UploadResult {
    data class Success(val dayCount: Int) : UploadResult()
    data class Failure(val code: Int, val body: String) : UploadResult()
}

class IntervalsClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultClient(),
) {

    fun putWellnessBulk(entries: List<WellnessEntry>): UploadResult {
        require(entries.isNotEmpty()) { "entries must not be empty" }
        val payload = JSONArray(entries.map { it.toJson() }).toString()
        val request = Request.Builder()
            .url("$baseUrl/api/v1/athlete/0/wellness-bulk")
            .header("Authorization", Credentials.basic(API_USERNAME, apiKey))
            .put(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            return if (response.isSuccessful) {
                UploadResult.Success(entries.size)
            } else {
                UploadResult.Failure(response.code, responseBody.take(300))
            }
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://intervals.icu"
        const val API_USERNAME = "API_KEY"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
