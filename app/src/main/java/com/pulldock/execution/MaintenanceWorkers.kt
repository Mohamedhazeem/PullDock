package com.pulldock.execution

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pulldock.domain.download.repository.MediaLibraryRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Deferrable maintenance jobs (T109, ADR-0005): recovery pass, library/file
 * reconciliation, and cleanup run on WorkManager — never on a continuous engine.
 * Scheduling is periodic with a network-connected constraint where needed; the work
 * itself is idempotent and Room-backed.
 */
class RecoveryWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    @Inject lateinit var library: MediaLibraryRepository

    override suspend fun doWork(): Result = try {
        // Periodic job reconciles files only. Engine recovery runs once at process
        // start (or boot), never while transfers are live in this process.
        library.reconcile()
        Result.success()
    } catch (_: Exception) {
        Result.retry() // deferrable: transient failure is retried by WorkManager
    }

    companion object {
        const val UNIQUE_NAME = "pulldock-recovery"
    }
}

object MaintenanceJobs {

    /** Schedules the periodic deferrable jobs; safe to call repeatedly. */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            RecoveryWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecoveryWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build(),
        )
    }
}
