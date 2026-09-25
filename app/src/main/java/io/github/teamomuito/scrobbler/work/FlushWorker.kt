package io.github.teamomuito.scrobbler.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.teamomuito.scrobbler.data.ScrobbleSubmitter
import io.github.teamomuito.scrobbler.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Sends queued scrobbles once there's a network connection, retrying with backoff. */
class FlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        when (applicationContext.graph.submitter.flush()) {
            ScrobbleSubmitter.Outcome.DONE -> Result.success()
            ScrobbleSubmitter.Outcome.RETRY -> Result.retry()
            ScrobbleSubmitter.Outcome.STOP -> Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "flush-scrobbles"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<FlushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            // Appending means a flush that's already running is followed by one that sees the new scrobble.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
