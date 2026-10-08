package icu.intervals.healthsync.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import icu.intervals.healthsync.model.MetricGroup

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    var lastSyncMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SYNC, value).apply()
        }

    var lastResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_RESULT, value).apply()
        }

    fun isGroupEnabled(group: MetricGroup): Boolean =
        prefs.getBoolean(groupKey(group), true)

    fun setGroupEnabled(group: MetricGroup, enabled: Boolean) {
        prefs.edit().putBoolean(groupKey(group), enabled).apply()
    }

    fun enabledGroups(): Set<MetricGroup> =
        MetricGroup.entries.filterTo(mutableSetOf()) { isGroupEnabled(it) }

    private fun groupKey(group: MetricGroup): String = "group_" + group.name.lowercase()

    private companion object {
        const val PREFS_FILE = "icu_health_sync"
        const val KEY_API_KEY = "api_key"
        const val KEY_LAST_SYNC = "last_sync_millis"
        const val KEY_LAST_RESULT = "last_result"
    }
}
