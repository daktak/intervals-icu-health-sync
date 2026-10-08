package icu.intervals.healthsync

import icu.intervals.healthsync.icu.IntervalsClient
import icu.intervals.healthsync.icu.UploadResult
import icu.intervals.healthsync.model.WellnessEntry
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IntervalsClientTest {

    @get:Rule
    val server: MockWebServer = MockWebServer()

    private fun client(apiKey: String = "test-key"): IntervalsClient =
        IntervalsClient(apiKey, server.url("/").toString().trimEnd('/'), OkHttpClient())

    @Test
    fun uploadsPayloadWithBasicAuth() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val result = client().putWellnessBulk(
            listOf(
                WellnessEntry(id = "2026-10-08", weight = 69.5, restingHR = 48L),
                WellnessEntry(id = "2026-10-07", sleepSecs = 25_200L),
            )
        )

        assertTrue(result is UploadResult.Success)
        assertEquals(2, (result as UploadResult.Success).dayCount)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/athlete/0/wellness-bulk", request.path!!)
        assertEquals(Credentials.basic("API_KEY", "test-key"), request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))

        val body = JSONArray(request.body.readUtf8())
        assertEquals(2, body.length())

        val first = body.getJSONObject(0)
        assertEquals("2026-10-08", first.getString("id"))
        assertEquals(69.5, first.getDouble("weight"), 0.0)
        assertEquals(48, first.getInt("restingHR"))
        assertFalse(first.has("hrv"))

        val second = body.getJSONObject(1)
        assertEquals("2026-10-07", second.getString("id"))
        assertEquals(25_200, second.getInt("sleepSecs"))
        assertFalse(second.has("weight"))
    }

    @Test
    fun reportsHttpFailureWithBody() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val result = client().putWellnessBulk(
            listOf(WellnessEntry(id = "2026-10-08", weight = 70.0))
        )

        assertTrue(result is UploadResult.Failure)
        result as UploadResult.Failure
        assertEquals(401, result.code)
        assertEquals("unauthorized", result.body)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyEntryList() {
        client().putWellnessBulk(emptyList())
    }
}
