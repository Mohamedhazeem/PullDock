package com.pulldock.data.download

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.DownloadRepository
import java.time.Instant
import kotlin.random.Random
import javax.inject.Inject

/**
 * Persisted retry with exponential backoff and jitter (T102, US4 scenario 4, FR-028).
 * A failed task re-enters the queue as RETRY_WAIT with nextRetryAt persisted; the
 * scheduler promotes it when the backoff elapses. Bounded by the policy's max retries
 * — exhausted tasks stay FAILED for explicit user retry (never loops forever).
 */
class RetryCoordinator @Inject constructor(
    private val repository: DownloadRepository,
) {

    /** Evaluates a task failure: schedules a retry or leaves it FAILED when exhausted. */
    suspend fun onFailure(task: DownloadTask, error: DownloadError, policy: DownloadPolicy) {
        if (!isRetryable(error)) return
        if (task.retryCount >= policy.maxRetries) return // stays FAILED; explicit retry only

        val backoff = jitteredBackoff(task.retryCount + 1, policy, Random.Default)
        val nextRetryAt = Instant.now().plusSeconds(backoff)
        repository.updateRetry(task.id, task.retryCount + 1, nextRetryAt)
        repository.updateState(task.id, DownloadState.RETRY_WAIT, error)
    }

    /** Promotes due RETRY_WAIT tasks back into the queue (called by the scheduler tick). */
    suspend fun promoteDue(tasks: List<DownloadTask>, now: Instant = Instant.now()) {
        tasks
            .filter { it.state == DownloadState.RETRY_WAIT && it.nextRetryAt?.isBefore(now) == true }
            .forEach { task ->
                repository.updateState(task.id, DownloadState.QUEUED)
            }
    }

    private fun isRetryable(error: DownloadError): Boolean = when (error) {
        is DownloadError.Network -> true
        is DownloadError.TorrentEngine -> error.reason in setOf(
            DownloadError.TorrentReason.NO_PEERS,
            DownloadError.TorrentReason.METADATA_TIMEOUT,
            DownloadError.TorrentReason.NO_TRACKERS,
        )
        // Extractor failures are worth a bounded retry: a task that was QUEUED behind
        // another download can fail at dispatch (e.g. the runtime was still settling or
        // a video-only selection had no audio stream). Retrying re-runs format selection
        // and the progressive-format fallback instead of stranding the item as FAILED.
        is DownloadError.ExtractorEngine -> true
        // A finalize/storage failure is often transient (state race, temp file busy).
        is DownloadError.Storage -> true
        else -> false
    }

    private fun jitteredBackoff(attempt: Int, policy: DownloadPolicy, random: Random): Long {
        val base = policy.backoffSeconds(attempt)
        val jitter = base * 0.2
        val low = (base - jitter).coerceAtLeast(1.0)
        val high = base + jitter
        return (low + random.nextDouble() * (high - low)).toLong()
    }
}
