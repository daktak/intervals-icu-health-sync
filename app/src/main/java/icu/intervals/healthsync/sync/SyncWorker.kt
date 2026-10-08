package icu.intervals.healthsync.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val daysBack = inputData.getInt(EXTRA_DAYS_BACK, 0)
        val outcome = SyncService(applicationContext)
            .sync(if (daysBack > 0) daysBack else null)
        val output = workDataOf(KEY_MESSAGE to outcome.message)
        return when {
            outcome.success -> Result.success(output)
            outcome.retryable -> Result.retry()
            else -> Result.failure(output)
        }
    }

    companion object {
        const val EXTRA_DAYS_BACK = "days_back"
        const val KEY_MESSAGE = "message"
        const val UNIQUE_PERIODIC = "icu_daily_sync"

        fun periodicRequest() = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
    }
}
