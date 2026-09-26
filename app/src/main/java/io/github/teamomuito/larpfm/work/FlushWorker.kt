package io.github.teamomuito.larpfm.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.teamomuito.larpfm.data.ScrobbleSubmitter
import io.github.teamomuito.larpfm.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Sends queued scrobbles once there's a network connection, retrying with backoff. */
class FlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        when (applicationContext.graph.submitter.flush()) {
            ScrobbleSubmitter.Outcome.DONE -> {
                applicationContext.graph.repository.msUntilNextCopy()?.let { LarpQueueWorker.schedule(applicationContext, it) }
                Result.success()
            }
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

/**
 * Wakes up when the next auto-LARP copy is due and starts a flush. It's separate from
 * [FlushWorker] so a flush can reschedule it without cancelling itself.
 */
class LarpQueueWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        FlushWorker.enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "larp-queue"

        /** A little slack so the copy's timestamp has definitely passed when the flush runs. */
        private const val SLACK_MS = 5_000L

        fun schedule(context: Context, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<LarpQueueWorker>()
                .setInitialDelay(delayMs + SLACK_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
