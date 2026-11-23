package icu.intervals.healthsync.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import android.text.method.LinkMovementMethod
import android.text.InputType
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.lifecycleScope
import icu.intervals.healthsync.BuildConfig
import icu.intervals.healthsync.R
import icu.intervals.healthsync.databinding.ActivityMainBinding
import icu.intervals.healthsync.model.MetricGroup
import icu.intervals.healthsync.sync.AppPreferences
import icu.intervals.healthsync.sync.SyncScheduler
import icu.intervals.healthsync.sync.SyncSchedule
import icu.intervals.healthsync.sync.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences

    private val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.apiKeyInput.setText(prefs.apiKey)
        binding.apiKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.apiKey = s?.toString()?.trim().orEmpty()
            }
        })

        var revealApiKey = false
        binding.apiKeyToggle.setOnClickListener {
            revealApiKey = !revealApiKey
            val cursor = binding.apiKeyInput.selectionStart
            if (revealApiKey) {
                binding.apiKeyInput.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.apiKeyToggle.setImageResource(R.drawable.ic_visibility_off)
                binding.apiKeyToggle.contentDescription = getString(R.string.desc_hide_api_key)
            } else {
                binding.apiKeyInput.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.apiKeyToggle.setImageResource(R.drawable.ic_visibility)
                binding.apiKeyToggle.contentDescription = getString(R.string.desc_show_api_key)
            }
            binding.apiKeyInput.setSelection(if (cursor >= 0) cursor else 0)
        }

        binding.groupCore.isChecked = prefs.isGroupEnabled(MetricGroup.CORE)
        binding.groupBody.isChecked = prefs.isGroupEnabled(MetricGroup.BODY_COMPOSITION)
        binding.groupVitals.isChecked = prefs.isGroupEnabled(MetricGroup.VITALS)
        binding.groupNutrition.isChecked = prefs.isGroupEnabled(MetricGroup.NUTRITION)

        binding.groupCore.setOnCheckedChangeListener { _, checked ->
            prefs.setGroupEnabled(MetricGroup.CORE, checked)
        }
        binding.groupBody.setOnCheckedChangeListener { _, checked ->
            prefs.setGroupEnabled(MetricGroup.BODY_COMPOSITION, checked)
        }
        binding.groupVitals.setOnCheckedChangeListener { _, checked ->
            prefs.setGroupEnabled(MetricGroup.VITALS, checked)
        }
        binding.groupNutrition.setOnCheckedChangeListener { _, checked ->
            prefs.setGroupEnabled(MetricGroup.NUTRITION, checked)
        }

        binding.morningSyncInput.setText(SyncSchedule.formatMinuteOfDay(prefs.morningSyncMinuteOfDay))
        binding.morningSyncInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val previous = prefs.morningSyncMinuteOfDay
                val parsed = SyncSchedule.parseMinuteOfDay(s?.toString())
                if (parsed == null) {
                    binding.morningSyncInput.removeTextChangedListener(this)
                    binding.morningSyncInput.setText(SyncSchedule.formatMinuteOfDay(previous))
                    binding.morningSyncInput.addTextChangedListener(this)
                    Toast.makeText(
                        this@MainActivity, R.string.error_invalid_time, Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                if (parsed != previous) {
                    prefs.morningSyncMinuteOfDay = parsed
                    // The configured slot moved — re-evaluate the stale check today.
                    prefs.lastForegroundRefreshEpochDay = -1
                    SyncScheduler.reschedulePeriodic(applicationContext, parsed)
                }
            }
        })

        binding.permissionsButton.setOnClickListener {
            lifecycleScope.launch {
                val granted = try {
                    HealthConnectClient.getOrCreate(this@MainActivity)
                        .permissionController
                        .getGrantedPermissions()
                } catch (e: Exception) {
                    emptySet<String>()
                }
                val missing = requiredPermissions - granted
                if (missing.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.permissions_already_granted,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                try {
                    permissionLauncher.launch(missing)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.health_connect_unavailable,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        binding.syncButton.setOnClickListener { runSync(null) }
        binding.backfillButton.setOnClickListener { runSync(BACKFILL_DAYS) }

        val versionLabel = "v${BuildConfig.VERSION_NAME}"
        val spannable = SpannableString(versionLabel)
        spannable.setSpan(
            URLSpan(getString(R.string.github_url)),
            0,
            versionLabel.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        binding.versionView.text = spannable
        binding.versionView.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        maybeEnqueueForegroundSync()
    }

    /**
     * Ensures today's overnight data is synced before the user decides on a
     * workout, in case the WorkManager slot drifted or fired before the
     * morning metrics were available. Runs at most once per day.
     */
    private fun maybeEnqueueForegroundSync() {
        if (prefs.apiKey.isBlank()) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val deadline = SyncSchedule.deadlineAt(
            today, prefs.morningSyncMinuteOfDay, zone, GRACE_MINUTES,
        )
        val lastSync = if (prefs.lastSyncMillis <= 0L) null else Instant.ofEpochMilli(prefs.lastSyncMillis).atZone(zone)

        val syncNeeded = lastSync == null || lastSync.isBefore(deadline)
        val epochDay = today.toEpochDay().toInt()
        if (syncNeeded && prefs.lastForegroundRefreshEpochDay != epochDay) {
            SyncScheduler.enqueueForegroundSync(applicationContext)
            prefs.lastForegroundRefreshEpochDay = epochDay
        }
    }

    private fun runSync(daysBack: Int?) {
        setButtonsEnabled(false)
        binding.statusView.text = getString(R.string.status_syncing)
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    SyncService(applicationContext).sync(daysBack)
                } catch (e: Exception) {
                    SyncService.Outcome(false, e.message ?: e.toString())
                }
            }
            setButtonsEnabled(true)
            if (!outcome.success) {
                Toast.makeText(this@MainActivity, outcome.message, Toast.LENGTH_LONG).show()
            }
            refreshStatus()
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.permissionsButton.isEnabled = enabled
        binding.syncButton.isEnabled = enabled
        binding.backfillButton.isEnabled = enabled
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val sdkLine = when (HealthConnectClient.getSdkStatus(this@MainActivity)) {
                HealthConnectClient.SDK_AVAILABLE -> "Health Connect: ready"
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    "Health Connect: update required"
                else -> "Health Connect: unavailable"
            }

            val granted = try {
                HealthConnectClient.getOrCreate(this@MainActivity)
                    .permissionController
                    .getGrantedPermissions()
            } catch (e: Exception) {
                emptySet<String>()
            }
            val missing = requiredPermissions - granted
            val permissionLine = if (missing.isEmpty()) {
                "Permissions: all granted"
            } else {
                "Permissions: ${missing.size} of ${requiredPermissions.size} missing"
            }

            val lastSyncLine = "Last sync: " + if (prefs.lastSyncMillis <= 0L) {
                "never"
            } else {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(prefs.lastSyncMillis))
            }
            val lastResultLine = "Last result: " + prefs.lastResult.ifEmpty { "-" }

            binding.statusView.text = listOf(
                sdkLine,
                permissionLine,
                lastSyncLine,
                lastResultLine,
            ).joinToString("\n")
        }
    }

    private companion object {
        const val BACKFILL_DAYS = 30
        private const val GRACE_MINUTES = 60L
    }
}
