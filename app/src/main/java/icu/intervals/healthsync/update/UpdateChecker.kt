package icu.intervals.healthsync.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import icu.intervals.healthsync.BuildConfig
import icu.intervals.healthsync.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UpdateChecker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun check(showToast: Boolean = false, scope: CoroutineScope? = null) {
        val runner = { doCheck(showToast) }
        if (scope != null) scope.launch(Dispatchers.IO) { runner() } else {
            Thread { runner() }.start()
        }
    }

    private fun doCheck(showToast: Boolean) {
        val json = try {
            val req = Request.Builder().url(UPDATE_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (e: Exception) {
            null
        }
        if (json == null) {
            if (showToast) main { Toast.makeText(context, R.string.update_error, Toast.LENGTH_LONG).show() }
            return
        }
        try {
            val release = JSONObject(json)
            val latest = release.getString("tag_name")
            val pre = release.optBoolean("prerelease", false)
            val current = BuildConfig.VERSION_NAME
            if (!pre && compareVersions(current, latest) < 0) {
                main {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) {
                    }
                }
            } else if (showToast) {
                main { Toast.makeText(context, R.string.update_already_latest, Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            if (showToast) main { Toast.makeText(context, R.string.update_error, Toast.LENGTH_LONG).show() }
        }
    }

    private fun main(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    companion object {
        const val UPDATE_URL = "https://api.github.com/repos/daktak/intervals-icu-health-sync/releases/latest"
        const val RELEASES_PAGE = "https://github.com/daktak/intervals-icu-health-sync/releases/latest"

        fun compareVersions(a: String, b: String): Int {
            val pa = parseVersion(a)
            val pb = parseVersion(b)
            val len = maxOf(pa.size, pb.size)
            for (i in 0 until len) {
                val x = if (i < pa.size) pa[i] else 0
                val y = if (i < pb.size) pb[i] else 0
                if (x != y) return Integer.compare(x, y)
            }
            return 0
        }

        private fun parseVersion(v: String?): IntArray {
            if (v == null) return intArrayOf(0)
            var s = v.trim()
            if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1)
            val parts = s.split("\\.")
            val out = IntArray(parts.size)
            for (i in parts.indices) out[i] = parsePart(parts[i])
            return out
        }

        private fun parsePart(part: String): Int {
            val sb = StringBuilder()
            for (c in part) {
                if (c.isDigit()) sb.append(c) else break
            }
            return if (sb.isEmpty()) 0 else sb.toString().toInt()
        }
    }
}