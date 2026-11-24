package com.pulldock.data.download

import com.pulldock.core.database.dao.RecoveryMarkerDao
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.queue.RecoveryReport
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.state.DownloadStateMachine
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentResult
import com.pulldock.domain.torrent.model.ResumeBlob
import com.pulldock.domain.torrent.model.TorrentSource
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Startup recovery (T071/T110, US9, FR-034/FR-064): interrupted tasks are restored from
 * resume data when available, otherwise re-added to the engine from their source. The
 * engine reuses the already-downloaded files in app storage and verifies pieces, so a
 * force-stop never leaves a "running" task with no live handle (data-model §4.3).
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
                task.type == DownloadType.TORRENT -> {
                    val details = repository.getTorrentDetails(task.id)
                    val blob = details?.resumeData
                    val restored = blob != null && importResumed(task.engineTaskId ?: task.id, blob)
                    if (restored && repository.updateState(task.id, DownloadState.QUEUED)) {
                        markerDao.delete(marker.taskId)
                        resumed += 1
                    } else {
                        // No usable resume blob: fall back to a fresh engine add below.
                        if (requeueFromSource(task.id)) resumed += 1 else {
                            repository.updateState(task.id, DownloadState.NEEDS_USER_ACTION)
                            needsUserAction += 1
                        }
                        markerDao.delete(marker.taskId)
                    }
                }
                else -> {
                    repository.updateState(task.id, DownloadState.QUEUED)
                    markerDao.delete(marker.taskId)
                    resumed += 1
                }
            }
        }

        // Tasks left in an active state by a force-stop may have no marker at all.
        repository.observeQueue(QueueFilter.ALL).first()
            .filter { it.state in DownloadStateMachine.activeStates && it.engineTaskId != null }
            .forEach { task ->
                if (requeueFromSource(task.id)) resumed += 1 else {
                    repository.updateState(task.id, DownloadState.NEEDS_USER_ACTION)
                    needsUserAction += 1
                }
            }

        return RecoveryReport(resumed = resumed, needsUserAction = needsUserAction, failed = failed)
    }

    private suspend fun importResumed(engineTaskId: String, blob: ResumeBlob): Boolean =
        when (torrentEngine.importResumeData(engineTaskId, blob)) {
            is TorrentResult.Success -> true
            is TorrentResult.Failure -> false
        }

    /** Re-adds a torrent from its persisted source; piece data on disk is reused. */
    private suspend fun requeueFromSource(taskId: String): Boolean {
        val task = repository.getTask(taskId) ?: return false
        if (task.type == DownloadType.TORRENT) {
            val source = if (task.source.startsWith("magnet:", ignoreCase = true)) {
                TorrentSource(TorrentSource.Kind.MAGNET, task.source)
            } else {
                TorrentSource(TorrentSource.Kind.TORRENT_URI, task.source)
            }
            when (torrentEngine.addTorrent(source)) {
                is TorrentResult.Success -> Unit
                is TorrentResult.Failure -> return false
            }
        }
        return repository.updateState(taskId, DownloadState.QUEUED)
    }

    /** Records an interruption marker for a task (called by resume checkpoints). */
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
