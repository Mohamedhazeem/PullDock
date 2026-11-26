package com.pulldock.data.download

import com.pulldock.core.database.entity.DownloadTaskEntity
import com.pulldock.core.database.entity.ExtractorTaskEntity
import com.pulldock.core.database.entity.TorrentFileEntity
import com.pulldock.core.database.entity.TorrentTaskEntity
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.ProviderId
import com.pulldock.core.model.StorageKind
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.extractor.model.ExtractorTaskDetails
import com.pulldock.domain.extractor.model.ProcessingStatus
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.torrent.model.ResumeBlob
import com.pulldock.domain.torrent.model.SeedingPolicy
import com.pulldock.domain.torrent.model.TorrentFile
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import java.time.Instant

/**
 * Entity ↔ domain mappers for the download queue (plan.md: data owns persistence
 * mapping). Mapping is total: unknown enum strings surface as typed persistence
 * failures at the boundary instead of silently defaulting.
 */
object TaskMappers {

    fun toDomain(entity: DownloadTaskEntity): DownloadTask = DownloadTask(
        id = entity.id,
        type = requireType(entity.type),
        source = entity.source,
        sourceHash = entity.sourceHash,
        displayName = entity.displayName,
        resolution = entity.resolution,
        outputFilename = entity.outputFilename,
        state = requireState(entity.state),
        progress = DownloadProgress(
            downloadedBytes = entity.downloadedBytes,
            totalBytes = entity.totalBytes,
            speedBytesPerSec = entity.downloadSpeed,
            etaSeconds = entity.etaSeconds,
        ),
        totalBytes = entity.totalBytes,
        downloadedBytes = entity.downloadedBytes,
        downloadSpeed = entity.downloadSpeed,
        uploadSpeed = entity.uploadSpeed,
        etaSeconds = entity.etaSeconds,
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt),
        completedAt = entity.completedAt?.let(Instant::ofEpochMilli),
        outputUri = entity.outputUri,
        errorCode = entity.errorCode,
        errorDetails = entity.errorDetails,
        retryCount = entity.retryCount,
        nextRetryAt = entity.nextRetryAt?.let(Instant::ofEpochMilli),
        destination = StorageDestination(
            kind = StorageKind.valueOf(entity.destinationKind),
            uri = entity.destinationUri,
            displayName = entity.destinationDisplayName,
            freeSpaceBytes = null,
        ),
        limits = TransferLimits(
            downloadRateBytesPerSec = entity.limitsDownloadRate,
            uploadRateBytesPerSec = entity.limitsUploadRate,
            maxConnections = entity.limitsMaxConnections,
        ),
        priority = entity.priority,
        queueOrder = entity.queueOrder,
        engineTaskId = entity.engineTaskId,
    )

    fun toEntity(task: DownloadTask, stateSequence: Long = 0): DownloadTaskEntity = DownloadTaskEntity(
        id = task.id,
        type = task.type.name,
        source = task.source,
        sourceHash = task.sourceHash,
        displayName = task.displayName,
        resolution = task.resolution,
        outputFilename = task.outputFilename,
        state = task.state.name,
        stateSequence = stateSequence,
        totalBytes = task.totalBytes,
        downloadedBytes = task.downloadedBytes,
        downloadSpeed = task.downloadSpeed,
        uploadSpeed = task.uploadSpeed,
        etaSeconds = task.etaSeconds,
        createdAt = task.createdAt.toEpochMilli(),
        updatedAt = task.updatedAt.toEpochMilli(),
        completedAt = task.completedAt?.toEpochMilli(),
        outputUri = task.outputUri,
        errorCode = task.errorCode,
        errorDetails = task.errorDetails,
        retryCount = task.retryCount,
        nextRetryAt = task.nextRetryAt?.toEpochMilli(),
        destinationKind = task.destination.kind.name,
        destinationUri = task.destination.uri,
        destinationDisplayName = task.destination.displayName,
        limitsDownloadRate = task.limits.downloadRateBytesPerSec,
        limitsUploadRate = task.limits.uploadRateBytesPerSec,
        limitsMaxConnections = task.limits.maxConnections,
        priority = task.priority,
        queueOrder = task.queueOrder,
        engineTaskId = task.engineTaskId,
    )

    fun toDomain(entity: TorrentTaskEntity, files: List<TorrentFileEntity>, state: DownloadState?): TorrentTaskDetails =
        TorrentTaskDetails(
            taskId = entity.taskId,
            infohash = entity.infohash,
            name = entity.name,
            totalSizeBytes = entity.totalSize,
            files = files.map(::toDomain),
            trackers = entity.trackers.split('\n').filter { it.isNotBlank() },
            peerCount = entity.peerCount,
            seedCount = entity.seedCount,
            sequentialDownload = entity.sequentialDownload,
            resumeData = entity.resumeData?.let { ResumeBlob(version = entity.resumeDataVersion, bytes = it) },
            seedingPolicy = SeedingPolicy(
                seedAfterCompletion = entity.seedAfterCompletion,
                maxUploadRateBytesPerSec = entity.maxUploadRateBytesPerSec,
                stopRatio = entity.stopRatio,
                timeLimitSeconds = entity.timeLimitSeconds,
                perTorrentOverride = entity.perTorrentOverride,
            ),
            uploadedBytes = entity.uploadedBytes,
            state = state,
        )

    fun toEntity(details: TorrentTaskDetails, trackers: String): TorrentTaskEntity = TorrentTaskEntity(
        taskId = details.taskId,
        infohash = details.infohash,
        name = details.name,
        totalSize = details.totalSizeBytes,
        trackers = trackers,
        peerCount = details.peerCount,
        seedCount = details.seedCount,
        sequentialDownload = details.sequentialDownload,
        resumeData = details.resumeData?.bytes,
        resumeDataVersion = details.resumeData?.version ?: 0,
        seedAfterCompletion = details.seedingPolicy.seedAfterCompletion,
        maxUploadRateBytesPerSec = details.seedingPolicy.maxUploadRateBytesPerSec,
        stopRatio = details.seedingPolicy.stopRatio,
        timeLimitSeconds = details.seedingPolicy.timeLimitSeconds,
        perTorrentOverride = details.seedingPolicy.perTorrentOverride,
        uploadedBytes = details.uploadedBytes,
    )

    fun toDomain(entity: TorrentFileEntity): TorrentFile = TorrentFile(
        index = entity.fileIndex,
        path = entity.path,
        sizeBytes = entity.sizeBytes,
        priority = entity.priority,
        selected = entity.selected,
    )

    fun toEntity(taskId: String, file: TorrentFile): TorrentFileEntity = TorrentFileEntity(
        taskId = taskId,
        fileIndex = file.index,
        path = file.path,
        sizeBytes = file.sizeBytes,
        priority = file.priority,
        selected = file.selected,
    )

    fun toDomain(entity: ExtractorTaskEntity): ExtractorTaskDetails = ExtractorTaskDetails(
        taskId = entity.taskId,
        providerId = ProviderId.valueOf(entity.providerId),
        originalUrl = entity.originalUrl,
        selectedFormat = entity.formatId?.let { formatId ->
            MediaFormat(
                id = formatId,
                resolution = entity.formatResolution,
                container = entity.formatContainer ?: "",
                videoCodec = entity.formatVideoCodec,
                audioCodec = entity.formatAudioCodec,
                estimatedBytes = entity.formatEstimatedBytes,
                mergeRequired = entity.formatMergeRequired ?: false,
                processingEstimateSeconds = entity.formatProcessingEstimateSeconds,
            )
        },
        container = entity.container,
        videoCodec = entity.videoCodec,
        audioCodec = entity.audioCodec,
        mergeRequired = entity.mergeRequired,
        processingStatus = ProcessingStatus.valueOf(entity.processingStatus),
        providerError = entity.providerError,
        runtimeInfoId = entity.runtimeInfoId,
    )

    fun toEntity(details: ExtractorTaskDetails): ExtractorTaskEntity = ExtractorTaskEntity(
        taskId = details.taskId,
        providerId = details.providerId.name,
        originalUrl = details.originalUrl,
        formatId = details.selectedFormat?.id,
        formatResolution = details.selectedFormat?.resolution,
        formatContainer = details.selectedFormat?.container,
        formatVideoCodec = details.selectedFormat?.videoCodec,
        formatAudioCodec = details.selectedFormat?.audioCodec,
        formatEstimatedBytes = details.selectedFormat?.estimatedBytes,
        formatMergeRequired = details.selectedFormat?.mergeRequired,
        formatProcessingEstimateSeconds = details.selectedFormat?.processingEstimateSeconds,
        container = details.container,
        videoCodec = details.videoCodec,
        audioCodec = details.audioCodec,
        mergeRequired = details.mergeRequired,
        processingStatus = details.processingStatus.name,
        providerError = details.providerError,
        runtimeInfoId = details.runtimeInfoId,
    )

    /** Unknown persisted enum values are a data bug, never a silent default. */
    private fun requireType(value: String): DownloadType =
        DownloadType.entries.firstOrNull { it.name == value }
            ?: throw IllegalStateException("Unknown persisted DownloadType: $value")

    private fun requireState(value: String): DownloadState =
        DownloadState.entries.firstOrNull { it.name == value }
            ?: throw IllegalStateException("Unknown persisted DownloadState: $value")
}
