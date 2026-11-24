package com.pulldock.data.download

import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.state.DownloadStateMachine

/**
 * Engine-event reconciliation (T104, invariant §3.6): progress and state events are
 * applied idempotently by task id + sequence. Out-of-order, duplicate, or stale events
 * never regress persisted state (a RUNNING→QUEUED event from an old engine tick is
 * ignored; progress only moves forward).
 */
class EventReconciler(private val repository: DownloadRepository) {

    /** Last applied sequence per task; entries are advisory (the DB is authoritative). */
    private val lastSequence = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Applies an engine event. Returns true when it changed persisted state; false when
     * it was a duplicate/stale/out-of-order event.
     */
    suspend fun onEngineEvent(
        taskId: String,
        sequence: Long,
        state: DownloadState?,
        downloadedBytes: Long?,
        totalBytes: Long?,
        speed: Long?,
    ): Boolean {
        if (sequence <= lastSequence.getOrDefault(taskId, 0L)) return false
        lastSequence[taskId] = sequence

        val task = repository.getTask(taskId) ?: return false

        var changed = false
        if (state != null && state != task.state) {
            // RUNNING -> QUEUED is legal only for startup recovery from persisted
            // resume data; an engine event must never regress a live transfer.
            val engineRegression = task.state == DownloadState.RUNNING && state == DownloadState.QUEUED
            if (!engineRegression && DownloadStateMachine.canTransition(task.state, state)) {
                changed = repository.updateState(taskId, state) || changed
            }
        }
        if (downloadedBytes != null) {
            val current = repository.getTask(taskId) ?: return changed
            if (downloadedBytes >= current.downloadedBytes) {
                val currentTotal = current.totalBytes
                val boundedTotal = if (totalBytes != null && currentTotal != null && totalBytes < currentTotal) {
                    currentTotal // never shrink a known total (invariant §3.5)
                } else {
                    totalBytes
                }
                repository.updateProgress(
                    taskId,
                    com.pulldock.domain.download.model.DownloadProgress(
                        downloadedBytes = downloadedBytes,
                        totalBytes = boundedTotal,
                        speedBytesPerSec = speed ?: current.downloadSpeed,
                        etaSeconds = etaFor(downloadedBytes, boundedTotal, speed ?: current.downloadSpeed),
                    ),
                )
                changed = true
            }
        }
        return changed
    }

    /** Estimated seconds to completion; null when inputs are unreliable. */
    private fun etaFor(downloaded: Long, total: Long?, speed: Long): Long? {
        if (total == null || speed <= 0) return null
        val remaining = (total - downloaded).coerceAtLeast(0)
        return if (remaining == 0L) 0 else remaining / speed
    }
}
