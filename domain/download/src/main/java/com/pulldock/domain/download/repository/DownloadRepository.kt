package com.pulldock.domain.download.repository

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import com.pulldock.domain.torrent.model.TorrentSource
import com.pulldock.domain.extractor.model.ExtractorTaskDetails
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Queue list filters (FR-023). */
enum class QueueFilter { ALL, ACTIVE, QUEUED, PAUSED, COMPLETED, FAILED }

/**
 * Persistence contract for the unified download queue. Implementations live in
 * `:data:download` on Room; the domain layer sees none of it.
 */
interface DownloadRepository {

    fun observeTask(id: String): Flow<DownloadTask?>
    fun observeQueue(filter: QueueFilter): Flow<List<DownloadTask>>
    fun observeTorrentDetails(taskId: String): Flow<TorrentTaskDetails?>
    fun observeExtractorDetails(taskId: String): Flow<ExtractorTaskDetails?>

    suspend fun getTask(id: String): DownloadTask?
    suspend fun getTorrentDetails(taskId: String): TorrentTaskDetails?
    suspend fun getExtractorDetails(taskId: String): ExtractorTaskDetails?

    /** Creates a torrent task; validates the source and returns the persisted task. */
    suspend fun addTorrent(source: TorrentSource, destination: StorageDestination): DownloadTask

    /** Creates a media task from a resolved descriptor and chosen format (FR-019). */
    suspend fun addMedia(descriptor: MediaDescriptor, format: MediaFormat, destination: StorageDestination): DownloadTask

    /**
     * Applies a state transition through the state machine (invariant §3.3); returns
     * `false` when the transition is illegal. Persists atomically with a timestamp and
     * monotonic sequence number.
     */
    suspend fun updateState(id: String, to: DownloadState, error: DownloadError? = null): Boolean

    suspend fun updateProgress(id: String, progress: DownloadProgress): Unit

    suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?): Unit

    /** Persists manual queue order (FR-028 priority/reorder). */
    suspend fun reorder(orderedIds: List<String>): Unit

    /**
     * Removes the task record (engine cancellation completed first); when
     * [deleteFile] is set, also deletes the finalized output. Distinct operations
     * (FR-027), both requiring user confirmation upstream (FR-041).
     */
    suspend fun removeTask(id: String, deleteFile: Boolean): Unit

    /** Duplicate lookup by deterministic source hash (invariant §3.2). */
    suspend fun findBySourceHash(hash: String): DownloadTask?

    /** Sets the engine-side handle id once the engine accepts the task. */
    suspend fun updateEngineTaskId(id: String, engineTaskId: String?): Unit

    /** Stores per-task limit overrides. */
    suspend fun updateLimits(id: String, limits: com.pulldock.core.model.TransferLimits): Unit
}
