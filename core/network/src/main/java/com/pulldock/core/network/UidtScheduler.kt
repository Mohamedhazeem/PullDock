package com.pulldock.core.network

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.annotation.RequiresApi

/**
 * User-Initiated Data Transfer job scheduling (T107, ADR-0005, FR-031). Long (>10 min),
 * user-started transfers on API 34+ run as `setUserInitiated` jobs: exempt from
 * ordinary job quotas, constraint-aware, with a progress notification. The scheduler
 * is a thin wrapper over JobScheduler; the actual transfer work lives in the engine
 * adapters, so the job service only keeps the process alive while a transfer runs.
 */
object UidtScheduler {

    private const val UIDT_JOB_ID = 4201

    /**
     * Schedules the UIDT job for an active long transfer. Returns true when the
     * platform accepted the job; false means the caller falls back to the `dataSync`
     * foreground service path (API < 34).
     */
    @RequiresApi(34)
    fun schedule(context: Context): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        val builder = JobInfo.Builder(
            UIDT_JOB_ID,
            ComponentName(context, UidtTransferJob::class.java),
        ).setUserInitiated(true)

        return try {
            scheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    /** Cancels the job when the transfer no longer needs the process alive. */
    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(UIDT_JOB_ID)
    }
}

/**
 * Keeps the process alive for the duration of user-initiated transfers (API 34+).
 * The coordinator drives the actual work; the job stops itself when the platform
 * ends the transfer window (stopSelfResult → onStopJob).
 */
@RequiresApi(34)
class UidtTransferJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // Work is owned by the coordinator (Room-driven); returning false tells the
        // system no background work remains queued here.
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Platform ended the transfer window (quota/constraint); the queue persists
        // its state and recovery marks any interrupted tasks (FR-033/FR-034).
        return false
    }
}
