package com.pulldock.domain.torrent.model

import com.pulldock.core.model.DownloadState

/**
 * One file inside a torrent (untrusted metadata from the engine; path sanitization and
 * traversal checks happen in the storage layer before any write, FR-047).
 */
data class TorrentFile(
    val index: Int,
    /** Relative path within the torrent; never absolute, never `..`. */
    val path: String,
    val sizeBytes: Long,
    /** Engine priority hint; semantics are engine-defined and mapped by the adapter. */
    val priority: Int,
    val selected: Boolean,
)

/** User file selection applied to a torrent (FR-004). */
data class FileSelection(
    val selectedIndexes: Set<Int>,
    /** Optional per-file priorities keyed by file index. */
    val priorityByIndex: Map<Int, Int> = emptyMap(),
) {
    init {
        require(priorityByIndex.keys.all { it in selectedIndexes || it >= 0 }) {
            "priority keys must be valid file indexes"
        }
    }
}

/**
 * Opaque, versioned engine resume blob (FR-010). Domain never parses the bytes
 * (data-model.md §4 "engine resume data is opaque"); [version] drives migration and
 * validation, and a non-resumable torrent simply has no blob.
 */
data class ResumeBlob(
    val version: Int,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is ResumeBlob && other.version == version && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = version * 31 + bytes.contentHashCode()
}

/** User-configurable seeding policy (FR-011/FR-012). Never seeds indefinitely. */
data class SeedingPolicy(
    val seedAfterCompletion: Boolean,
    val maxUploadRateBytesPerSec: Long?,
    /** Upload/download ratio at which seeding stops; null disables the ratio check. */
    val stopRatio: Double?,
    /** Seeding time limit in seconds; null disables the time limit. */
    val timeLimitSeconds: Long?,
    /** Per-torrent override flag in addition to the global policy. */
    val perTorrentOverride: Boolean,
) {
    init {
        require(stopRatio == null || stopRatio > 0) { "stopRatio must be positive" }
        require(timeLimitSeconds == null || timeLimitSeconds > 0) { "timeLimitSeconds must be positive" }
        require(maxUploadRateBytesPerSec == null || maxUploadRateBytesPerSec >= 0) { "upload rate must be >= 0" }
    }

    /**
     * `true` when the policy guarantees seeding stops: either seeding is off, or at
     * least one stop condition (ratio/time limit) is configured. Invariant FR-012.
     */
    val stopsSeeding: Boolean
        get() = !seedAfterCompletion || stopRatio != null || timeLimitSeconds != null

    companion object {
        /** v1 default: seed after completion, stop at ratio 1.0 or 30 minutes. */
        val DEFAULT = SeedingPolicy(
            seedAfterCompletion = true,
            maxUploadRateBytesPerSec = null,
            stopRatio = 1.0,
            timeLimitSeconds = 1800,
            perTorrentOverride = false,
        )
    }
}

/** Engine-specific torrent detail keyed by [taskId] (data-model.md §1.2). */
data class TorrentTaskDetails(
    val taskId: String,
    val infohash: String,
    /** Torrent name from metadata; untrusted — sanitized before display/storage. */
    val name: String,
    val totalSizeBytes: Long,
    val files: List<TorrentFile>,
    val trackers: List<String>,
    val peerCount: Int,
    /** Number of seeds among connected peers (libtorrent num_seeds). */
    val seedCount: Int = 0,
    val sequentialDownload: Boolean,
    val resumeData: ResumeBlob?,
    val seedingPolicy: SeedingPolicy,
    val uploadedBytes: Long,
    val state: DownloadState?,
)
