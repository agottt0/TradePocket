package com.tradelog.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tradelog.data.repo.TradeRepository
import java.util.concurrent.TimeUnit

/**
 * Background mail scan. Runs only on an unmetered-agnostic connected network and
 * retries with WorkManager's backoff on transient failures.
 */
class MailSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = TradeRepository.get(applicationContext)
        val outcome = repo.sync(fullRescan = false)
        return when {
            outcome.ok -> Result.success()
            // A config problem will not fix itself; retrying just burns battery.
            outcome.error?.contains("未配置") == true -> Result.failure()
            outcome.error?.contains("认证") == true -> Result.failure()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "mail-sync-periodic"
        private const val MAX_ATTEMPTS = 3

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<MailSyncWorker>(
                intervalHours.coerceIn(1, 24).toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // UPDATE keeps the existing schedule when only the interval changed.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
