package com.pulldock.data.download

import com.pulldock.core.database.dao.RecoveryMarkerDao
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.queue.RecoveryReport
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentResult
import com.pulldock.domain.torrent.model.ResumeBlob
import javax.inject.Inject

/**
 * Startup recovery (T071, US9, FR-034/FR-064): interrupted tasks are either restored
 * from persisted resume data or explicitly marked `NEEDS_USER_ACTION` — never silently
 * dropped. Torrent recovery restores the engine session from the versioned resume
 * blob; extractor tasks re-enter the queue for a fresh resolve (no engine state to
 * rebuild).
 */
class Recovery @Inject constructor(
    private val repository: DownloadRepository,
    private val markerDao: RecoveryMarkerDao,
    private val torrentEngine: TorrentEngine,
) {

    suspend fun recover(): RecoveryReport {
        val markers = markerDao.getRecoverable()
        var resumed = 0
        var needsUserAction = 0
        var failed = 0

        markers.forEach { marker ->
            markerDao.incrementAttempts(marker.taskId)
            val task = repository.getTask(marker.taskId)
            when {
                task == null -> {
                    markerDao.delete(marker.taskId)
                    failed += 1
                }
                task.type == com.pulldock.core.model.DownloadType.TORRENT -> {
                    val details = repository.getTorrentDetails(task.id)
                    val blob = details?.resumeData
                    val restored = blob != null && run {
                        when (val result = torrentEngine.importResumeData(task.engineTaskId ?: task.id, blob)) {
                            is TorrentResult.Success -> true
                            is TorrentResult.Failure -> false
                        }
                    } && repository.updateState(task.id, DownloadState.QUEUED)
                    if (restored) {
                        markerDao.delete(marker.taskId)
                        resumed += 1
                    } else {
                        // Engine state cannot be reconstructed: explicit user action.
                        repository.updateState(task.id, DownloadState.NEEDS_USER_ACTION)
                        needsUserAction += 1
                    }
                }
                else -> {
                    // Extractor tasks carry no engine-side state to rebuild.
                    repository.updateState(task.id, DownloadState.QUEUED)
                    resumed += 1
                }
            }
        }
        return RecoveryReport(resumed = resumed, needsUserAction = needsUserAction, failed = failed)
    }

    /** Records an interruption marker for a task (called on unexpected shutdown paths). */
    suspend fun markInterrupted(taskId: String, reason: String, resumeDataPresent: Boolean) {
        markerDao.upsert(
            com.pulldock.core.database.entity.RecoveryMarkerEntity(
                taskId = taskId,
                interruptedAt = System.currentTimeMillis(),
                reason = reason,
                resumeDataPresent = resumeDataPresent,
                recoverable = resumeDataPresent,
                attempts = 0,
            ),
        )
    }

}
