package com.pulldock.domain.download.queue

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.model.DownloadTask
import kotlinx.coroutines.flow.StateFlow

/**
 * Snapshot of the unified queue (FR-023/FR-028). The coordinator is the single source
 * of truth for scheduling across both engine types.
 */
data class QueueState(
    val tasks: List<DownloadTask>,
    val activeCount: Int,
    val queuedCount: Int,
    val globalLimit: Int,
    val perEngineLimits: Map<DownloadType, Int>,
)

/** Recovery report from startup recovery (US9, FR-034). */
data class RecoveryReport(val resumed: Int, val needsUserAction: Int, val failed: Int)

/**
 * The single scheduler for torrent and extractor transfers (ADR-0004). Contract per
 * contracts/repositories.md: per-engine caps inside a global cap, round-robin fairness,
 * bounded retry with persisted backoff, duplicate prevention via `sourceHash`,
 * idempotent pause/resume/cancel (FR-026), and recovery driven by persisted markers.
 *
 * Implementations persist every transition and never call engines from the UI thread.
 */
interface QueueCoordinator {

    /** Hot, conflated queue state; collection never awaits a change for the first value. */
    val state: StateFlow<QueueState>

    /** Adds a persisted task to the queue (duplicate-safe). */
    suspend fun enqueue(task: DownloadTask): Unit

    /** Idempotent pause (FR-026). */
    suspend fun pause(id: String): Unit

    /** Idempotent resume; re-enters the queue without duplicating work. */
    suspend fun resume(id: String): Unit

    /** Cooperative cancellation; persists CANCELLED and stops the engine session. */
    suspend fun cancel(id: String): Unit

    /** Bounded retry of a failed task (FR/US4 scenario 4); never loops forever. */
    suspend fun retry(id: String): Unit

    /** Manual reorder; persists the new order. */
    suspend fun move(ids: List<String>): Unit

    /** Removes the task; engine cancellation completes first. */
    suspend fun remove(id: String, deleteFile: Boolean): Unit

    /**
     * Startup recovery (US9): restores from persisted state and recovery markers; tasks
     * whose engine state cannot be reconstructed are marked `NEEDS_USER_ACTION`, never
     * silently dropped.
     */
    suspend fun recover(): RecoveryReport

    fun observeState(): StateFlow<QueueState> = state

    /**
     * Applies an out-of-band engine event idempotently (task id + sequence, T104);
     * out-of-order or duplicate events never corrupt persisted state.
     */
    suspend fun onEngineEvent(taskId: String, sequence: Long, progressBytes: Long?, totalBytes: Long?, speed: Long?, state: com.pulldock.core.model.DownloadState?, error: DownloadError?): Unit
}
