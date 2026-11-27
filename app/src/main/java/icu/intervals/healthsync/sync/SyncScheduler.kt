package icu.intervals.healthsync.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZonedDateTime

/**
 * Central place for scheduling sync work.
 *
 * All scheduling goes through [ExistingPeriodicWorkPolicy.UPDATE] so changing
 * the configured time actually replaces the pending work instead of being
 * silently ignored ([ExistingPeriodicWorkPolicy.KEEP]).
 */
object SyncScheduler {

    const val FOREGROUND_SYNC = "icu_foreground_sync"

    fun reschedulePeriodic(context: Context, minuteOfDay: Int, now: ZonedDateTime = ZonedDateTime.now()) {
        val delayMillis = SyncSchedule.nextDelayMillis(now, minuteOfDay)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            SyncWorker.periodicRequest(delayMillis),
        )
    }

    fun enqueueForegroundSync(context: Context) {
        // No daysBack filter → the normal window (lastSync-1 .. today) is used,
        // which already covers today's overnight data.
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FOREGROUND_SYNC,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
