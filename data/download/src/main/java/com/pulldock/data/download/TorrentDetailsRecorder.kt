package com.pulldock.data.download

import com.pulldock.core.database.dao.DownloadTaskDao
import com.pulldock.core.database.dao.TorrentTaskDao
import com.pulldock.core.storage.FilenameSanitizer
import com.pulldock.domain.torrent.TorrentMetadata
import com.pulldock.domain.torrent.model.SeedingPolicy
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists engine metadata/telemetry (FR-003, FR-044, US4). Metadata is untrusted:
 * names are sanitized before display/persistence and tracker URLs are stored raw only
 * in the detail row (never logged).
 */
@Singleton
class TorrentDetailsRecorder @Inject constructor(
    private val taskDao: DownloadTaskDao,
    private val torrentDao: TorrentTaskDao,
) {

    suspend fun recordMetadata(taskId: String, metadata: TorrentMetadata) {
        val name = FilenameSanitizer.sanitize(metadata.name).take(MAX_NAME_LENGTH).ifBlank { "Torrent" }
        val details = TorrentTaskDetails(
            taskId = taskId,
            infohash = metadata.infohash,
            name = name,
            totalSizeBytes = metadata.totalSizeBytes,
            files = metadata.files,
            trackers = metadata.trackers,
            peerCount = 0,
            sequentialDownload = false,
            resumeData = null,
            seedingPolicy = SeedingPolicy.DEFAULT,
            uploadedBytes = 0,
            state = null,
        )
        torrentDao.upsert(TaskMappers.toEntity(details, metadata.trackers.joinToString("\n")))
        torrentDao.replaceFiles(taskId, metadata.files.map { TaskMappers.toEntity(taskId, it) })
        taskDao.updateMetadata(taskId, name, metadata.totalSizeBytes, System.currentTimeMillis())
    }

    suspend fun updateStats(taskId: String, peerCount: Int, seedCount: Int, uploadedBytes: Long) {
        torrentDao.updateStats(taskId, uploadedBytes.coerceAtLeast(0), peerCount.coerceAtLeast(0), seedCount.coerceAtLeast(0))
    }

    suspend fun updateUploadSpeed(taskId: String, bytesPerSec: Long) {
        taskDao.updateUploadSpeed(taskId, bytesPerSec.coerceAtLeast(0), System.currentTimeMillis())
    }

    private companion object {
        const val MAX_NAME_LENGTH = 160
    }
}
