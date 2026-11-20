package icu.intervals.healthsync.sync

import android.app.Application
import androidx.work.WorkManager

class SyncApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.reschedulePeriodic(this, AppPreferences(this).morningSyncMinuteOfDay)
    }
}
