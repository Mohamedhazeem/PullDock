package com.pulldock.data.download

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.core.database.dao.DownloadTaskDao
import com.pulldock.core.database.dao.ExtractorTaskDao
import com.pulldock.core.database.dao.TorrentTaskDao
import com.pulldock.core.database.entity.DownloadTaskEntity
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.LibraryQuery
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.extractor.model.ExtractorTaskDetails
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.torrent.model.TorrentSource
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [DownloadRepository] (contracts/repositories.md).
 * State transitions are validated by the domain state machine before they touch the
 * database and persisted atomically with a timestamp and per-task sequence number
 * (invariant §3.3, FR-029).
 */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val taskDao: DownloadTaskDao,
    private val torrentDao: TorrentTaskDao,
    private val extractorDao: ExtractorTaskDao,
    private val dispatchers: AppDispatchers,
) : DownloadRepository {

    override fun observeTask(id: String): Flow<DownloadTask?> =
        taskDao.observeById(id).map { it?.let(TaskMappers::toDomain) }

    override fun observeQueue(filter: QueueFilter): Flow<List<DownloadTask>> {
        val entities: Flow<List<DownloadTaskEntity>> = when (filter) {
            QueueFilter.ALL -> taskDao.observeAll()
            QueueFilter.ACTIVE -> taskDao.observeActive()
            QueueFilter.QUEUED -> taskDao.observeQueuedAndActive()
            QueueFilter.PAUSED -> taskDao.observePaused()
            QueueFilter.COMPLETED -> taskDao.observeCompleted()
            QueueFilter.FAILED -> taskDao.observeFailed()
        }
        return entities.map { list -> list.map(TaskMappers::toDomain) }
    }

    override fun observeTorrentDetails(taskId: String): Flow<TorrentTaskDetails?> =
        kotlinx.coroutines.flow.combine(
            torrentDao.observeByTaskId(taskId),
            torrentDao.observeFiles(taskId),
            taskDao.observeById(taskId),
        ) { entity, files, task ->
            entity?.let { TaskMappers.toDomain(it, files, task?.state?.let(::toState)) }
        }

    override fun observeExtractorDetails(taskId: String): Flow<ExtractorTaskDetails?> =
        extractorDao.observeByTaskId(taskId).map { it?.let(TaskMappers::toDomain) }

    override suspend fun getTask(id: String): DownloadTask? = taskDao.getById(id)?.let(TaskMappers::toDomain)

    override suspend fun getTorrentDetails(taskId: String): TorrentTaskDetails? {
        val entity = torrentDao.getByTaskId(taskId) ?: return null
        val files = torrentDao.getFiles(taskId)
        val state = taskDao.getById(taskId)?.state?.let(::toState)
        return TaskMappers.toDomain(entity, files, state)
    }

    override suspend fun getExtractorDetails(taskId: String): ExtractorTaskDetails? =
        extractorDao.getByTaskId(taskId)?.let(TaskMappers::toDomain)

    override suspend fun addTorrent(source: TorrentSource, destination: StorageDestination): DownloadTask {
        val now = Instant.now()
        val sourceHash = com.pulldock.domain.download.model.SourceHashing.forTorrent(source)
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            type = DownloadType.TORRENT,
            source = source.value,
            sourceHash = sourceHash,
            displayName = sanitizedDisplayName(source),
            outputFilename = "",
            state = DownloadState.CREATED,
            progress = DownloadProgress.IDLE,
            totalBytes = null,
            downloadedBytes = 0,
            downloadSpeed = 0,
            uploadSpeed = null,
            etaSeconds = null,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            outputUri = null,
            errorCode = null,
            errorDetails = null,
            retryCount = 0,
            nextRetryAt = null,
            destination = destination,
            limits = TransferLimits.NONE,
            priority = 0,
            queueOrder = Int.MAX_VALUE,
            engineTaskId = null,
        )
        taskDao.insert(TaskMappers.toEntity(task))
        return task
    }

    override suspend fun addMedia(descriptor: MediaDescriptor, format: MediaFormat, destination: StorageDestination): DownloadTask {
        val now = Instant.now()
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            type = DownloadType.EXTRACTOR,
            source = descriptor.sourceUrl,
            sourceHash = com.pulldock.domain.download.model.SourceHashing.forMedia(descriptor.sourceUrl),
            displayName = descriptor.title,
            resolution = format.resolution,
            outputFilename = "",
            state = DownloadState.CREATED,
            progress = DownloadProgress.IDLE,
            totalBytes = format.estimatedBytes,
            downloadedBytes = 0,
            downloadSpeed = 0,
            uploadSpeed = null,
            etaSeconds = null,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            outputUri = null,
            errorCode = null,
            errorDetails = null,
            retryCount = 0,
            nextRetryAt = null,
            destination = destination,
            limits = TransferLimits.NONE,
            priority = 0,
            queueOrder = Int.MAX_VALUE,
            engineTaskId = null,
        )
        taskDao.insert(TaskMappers.toEntity(task))
        extractorDao.upsert(
            ExtractorTaskDetails(
                taskId = task.id,
                providerId = descriptor.providerId,
                originalUrl = descriptor.sourceUrl,
                selectedFormat = format,
                container = format.container,
                videoCodec = format.videoCodec,
                audioCodec = format.audioCodec,
                mergeRequired = format.mergeRequired,
                processingStatus = com.pulldock.domain.extractor.model.ProcessingStatus.NONE,
                providerError = null,
                runtimeInfoId = null,
            ).let(TaskMappers::toEntity),
        )
        return task
    }

    override suspend fun updateState(id: String, to: DownloadState, error: DownloadError?): Boolean {
        val current = taskDao.getById(id) ?: return false
        val from = toState(current.state) ?: return false
        // Domain guard first: the database never accepts an illegal transition (§3.3).
        if (com.pulldock.domain.download.state.DownloadStateMachine.transition(from, to) == null) return false
        return taskDao.updateState(
            id = id,
            state = to.name,
            errorCode = error?.code,
            errorDetails = error?.let { it::class.simpleName },
            updatedAt = System.currentTimeMillis(),
        ) > 0
    }

    override suspend fun updateProgress(id: String, progress: DownloadProgress) {
        taskDao.updateProgress(
            id = id,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            downloadSpeed = progress.speedBytesPerSec,
            etaSeconds = progress.etaSeconds,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?) {
        taskDao.updateRetry(id, count, nextRetryAt?.toEpochMilli(), System.currentTimeMillis())
    }

    override suspend fun reorder(orderedIds: List<String>) {
        taskDao.reorder(orderedIds)
    }

    override suspend fun removeTask(id: String, deleteFile: Boolean) {
        // Row-level detail rows cascade (data-model.md §4.3). File deletion is the
        // caller's distinct, confirmed operation (FR-027) executed via storage.
        taskDao.delete(id)
    }

    override suspend fun findBySourceHash(hash: String): DownloadTask? =
        taskDao.findBySourceHash(hash)?.let(TaskMappers::toDomain)

    override suspend fun updateEngineTaskId(id: String, engineTaskId: String?) {
        taskDao.updateEngineTaskId(id, engineTaskId, System.currentTimeMillis())
    }

    override suspend fun updateLimits(id: String, limits: TransferLimits) {
        taskDao.updateLimits(id, limits.downloadRateBytesPerSec, limits.uploadRateBytesPerSec, limits.maxConnections, System.currentTimeMillis())
    }

    private fun toState(value: String): DownloadState? =
        DownloadState.entries.firstOrNull { it.name == value }

    /**
     * Display name before metadata arrives: torrent URIs show the file reference only
     * (no raw URI with potential query params), magnets a stable label (FR-049).
     */
    private fun sanitizedDisplayName(source: TorrentSource): String = when (source.kind) {
        com.pulldock.domain.torrent.model.TorrentSource.Kind.MAGNET -> "Torrent"
        com.pulldock.domain.torrent.model.TorrentSource.Kind.TORRENT_URI -> "Torrent file"
    }
}
