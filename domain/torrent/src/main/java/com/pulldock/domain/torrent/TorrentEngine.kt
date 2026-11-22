package com.pulldock.domain.torrent

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.torrent.model.FileSelection
import com.pulldock.domain.torrent.model.ResumeBlob
import com.pulldock.domain.torrent.model.SeedingPolicy
import com.pulldock.domain.torrent.model.TorrentFile
import com.pulldock.domain.torrent.model.TorrentSource
import java.time.Duration
import kotlinx.coroutines.flow.Flow

/** Opaque engine-side task handle. Domain never parses it (data-model.md §1.5). */
typealias EngineTaskId = String

/** Typed torrent operation result (contracts/torrent-engine.md). */
sealed interface TorrentResult<out T> {
    data class Success<T>(val value: T) : TorrentResult<T>
    data class Failure(val error: DownloadError) : TorrentResult<Nothing>
}

/** Torrent metadata fetched asynchronously (FR-003). */
data class TorrentMetadata(
    val name: String,
    val infohash: String,
    val totalSizeBytes: Long,
    val files: List<TorrentFile>,
    val trackers: List<String>,
)

/** Loopback streaming handle (US6, contracts/streaming-bridge.md). */
data class StreamingHandle(
    val url: String,
    val token: String,
    val fileIndex: Int,
)

/** Typed engine events; carry no native types (constitution VI). */
sealed interface TorrentEvent {
    data class MetadataAvailable(val taskId: EngineTaskId, val metadata: TorrentMetadata) : TorrentEvent
    data class Progress(
        val taskId: EngineTaskId,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSec: Long,
        val uploadedBytes: Long,
    ) : TorrentEvent

    data class PeerCount(val taskId: EngineTaskId, val peers: Int) : TorrentEvent
    data class PieceVerified(val taskId: EngineTaskId, val failedPieces: Int) : TorrentEvent
    data class StateChanged(val taskId: EngineTaskId, val state: DownloadState) : TorrentEvent
    data class Error(val taskId: EngineTaskId, val error: DownloadError) : TorrentEvent
    data class SeedingStats(val taskId: EngineTaskId, val uploadedBytes: Long, val ratio: Double) : TorrentEvent
}

/**
 * Torrent engine contract (FR-001–FR-013, ADR-0001). All calls are safe to issue from
 * `Dispatchers.Io`; the adapter marshals to the engine thread. `pause`/`resume`/
 * `cancel`/`close` are idempotent. Implementations translate engine alerts into
 * [TorrentEvent]s and never expose libtorrent types.
 */
interface TorrentEngine {

    /** Adds a magnet URI or torrent file reference; returns the engine task id. */
    suspend fun addTorrent(source: TorrentSource): TorrentResult<EngineTaskId>

    /** Fetches metadata asynchronously; times out with a typed error (FR-005). */
    suspend fun fetchMetadata(taskId: EngineTaskId, timeout: Duration): TorrentResult<TorrentMetadata>

    /** Selects/prioritizes files; safe before or during download (FR-004). */
    suspend fun selectFiles(taskId: EngineTaskId, selection: FileSelection): TorrentResult<Unit>

    /** Enables sequential downloading (streaming eligibility, US6). */
    suspend fun setSequential(taskId: EngineTaskId, enabled: Boolean): TorrentResult<Unit>

    /** Starts the transfer; duplicate starts never create a second session (FR-026). */
    suspend fun start(taskId: EngineTaskId): TorrentResult<Unit>

    /** Idempotent pause. */
    suspend fun pause(taskId: EngineTaskId): TorrentResult<Unit>

    /** Idempotent resume; no-op when already running. */
    suspend fun resume(taskId: EngineTaskId): TorrentResult<Unit>

    /** Re-verifies pieces (FR-009). */
    suspend fun recheck(taskId: EngineTaskId): TorrentResult<Unit>

    /** Updates per-file priorities. */
    suspend fun setFilePriority(taskId: EngineTaskId, priorities: Map<Int, Int>): TorrentResult<Unit>

    /** Applies per-task rate/connection limits (FR-008). */
    suspend fun setLimits(taskId: EngineTaskId, limits: com.pulldock.core.model.TransferLimits): TorrentResult<Unit>

    /** Applies the seeding policy (FR-011/FR-012). */
    suspend fun setSeedingPolicy(taskId: EngineTaskId, policy: SeedingPolicy): TorrentResult<Unit>

    /** Removes the task from the engine. Does not delete files (FR-027). */
    suspend fun remove(taskId: EngineTaskId): TorrentResult<Unit>

    /** Opaque, versioned resume blob; null when unavailable (FR-010). */
    suspend fun exportResumeData(taskId: EngineTaskId): TorrentResult<ResumeBlob?>

    /** Restores engine state from a resume blob. */
    suspend fun importResumeData(taskId: EngineTaskId, blob: ResumeBlob): TorrentResult<Unit>

    /** Loopback streaming URL for an eligible media file (US6). */
    suspend fun requestStreamingBridge(taskId: EngineTaskId, fileIndex: Int): TorrentResult<StreamingHandle>

    /** Hot event stream; pass null for all tasks. Throttled by the adapter. */
    fun observeEvents(taskId: EngineTaskId?): Flow<TorrentEvent>

    /** Cooperative cancellation of in-flight work for a task. */
    fun cancel(taskId: EngineTaskId)

    /** Idempotent session shutdown; called from the process-death path. */
    suspend fun close()
}
