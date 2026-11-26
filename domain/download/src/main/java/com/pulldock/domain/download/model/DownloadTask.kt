package com.pulldock.domain.download.model

import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.StorageKind
import com.pulldock.core.model.TransferLimits
import java.time.Instant

/**
 * Progress of a transfer (data-model.md §1.4). `percent` is always derived from the
 * byte counts — never stored as an authority — and is `null` while `totalBytes` is
 * unknown.
 */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
) {
    /** Derived completion fraction in `[0, 1]`; null when the total is unknown. */
    val percent: Double?
        get() = totalBytes?.takeIf { it > 0 }?.let { downloadedBytes.coerceIn(0, it).toDouble() / it }

    companion object {
        /** Progress for a task that has not transferred anything yet. */
        val IDLE = DownloadProgress(downloadedBytes = 0, totalBytes = null, speedBytesPerSec = 0, etaSeconds = null)
    }
}

/**
 * Where a download's output is published. Pure-domain view of the platform
 * `ResolvedDestination` (core:storage); the data layer maps between them.
 */
data class StorageDestination(
    val kind: StorageKind,
    /** SAF tree/document URI when [kind] is SAF; null for platform-managed locations. */
    val uri: String?,
    val displayName: String,
    val freeSpaceBytes: Long?,
)

/**
 * Global queue/transfer policy with the v1 defaults from data-model.md §1.4. Values are
 * user-overridable and persisted (NetworkPolicyStore, T105).
 */
data class DownloadPolicy(
    val maxConcurrent: Int = 3,
    val torrentMaxConcurrent: Int = 2,
    val extractorMaxConcurrent: Int = 1,
    val defaultLimits: TransferLimits = TransferLimits.NONE,
    val maxRetries: Int = 3,
    val initialBackoffSeconds: Long = 5,
    val maxBackoffSeconds: Long = 900,
    val backoffMultiplier: Double = 2.0,
    val wifiOnly: Boolean = false,
    val meteredAllowed: Boolean = true,
    /** Pause active downloads below this battery percent; 0 disables. */
    val batteryThresholdPercent: Int = 15,
) {
    init {
        require(maxConcurrent >= 1) { "maxConcurrent must be >= 1" }
        require(torrentMaxConcurrent >= 0 && extractorMaxConcurrent >= 0) { "per-engine limits must be >= 0" }
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        require(initialBackoffSeconds > 0) { "initialBackoffSeconds must be positive" }
        require(maxBackoffSeconds >= initialBackoffSeconds) { "maxBackoffSeconds must not be below the initial backoff" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must not shrink the backoff" }
        require(batteryThresholdPercent in 0..100) { "batteryThresholdPercent must be 0..100" }
    }

    /** Exponential backoff (±20% jitter applied by the caller) for the given attempt, 1-based. */
    fun backoffSeconds(attempt: Int): Long {
        val base = initialBackoffSeconds * Math.pow(backoffMultiplier, (attempt - 1).coerceAtLeast(0).toDouble())
        return base.toLong().coerceAtMost(maxBackoffSeconds)
    }
}

/**
 * The unified download task (data-model.md §1.1). No engine-specific type appears here
 * (invariant §3.9); torrent and extractor details live in their own domain models keyed
 * by [id].
 */
data class DownloadTask(
    val id: String,
    val type: DownloadType,
    /** Sanitized source (magnet URI, torrent reference, or media URL). */
    val source: String,
    /** Deterministic hash used for duplicate detection; unique (invariant §3.2). */
    val sourceHash: String,
    val displayName: String,
    /** Selected format resolution (e.g. "720p"); null for torrents/unknown. */
    val resolution: String? = null,
    val outputFilename: String,
    val state: DownloadState,
    val progress: DownloadProgress,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val downloadSpeed: Long,
    val uploadSpeed: Long?,
    val etaSeconds: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val outputUri: String?,
    val errorCode: String?,
    val errorDetails: String?,
    val retryCount: Int,
    val nextRetryAt: Instant?,
    val destination: StorageDestination,
    val limits: TransferLimits,
    /** User-set priority; higher runs first. */
    val priority: Int,
    /** Manual queue order; lower runs first within the same priority. */
    val queueOrder: Int,
    val engineTaskId: String?,
)
