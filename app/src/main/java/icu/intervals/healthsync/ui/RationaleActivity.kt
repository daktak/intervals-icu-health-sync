package icu.intervals.healthsync.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import icu.intervals.healthsync.databinding.ActivityRationaleBinding

/**
 * Privacy policy behind Health Connect's consent-screen links. Health Connect
 * refuses to show the permission dialog at all unless the requesting app
 * resolves VIEW_PERMISSION_USAGE / HEALTH_PERMISSIONS (and on Android <= 13,
 * androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE) - see the manifest.
 */
class RationaleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRationaleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.closeButton.setOnClickListener { finish() }
    }
}
