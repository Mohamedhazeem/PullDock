package com.pulldock.data.download

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.state.DownloadStateMachine
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Checkpoints engine resume data for running torrents (FR-010/FR-029, ADR-0005).
 * libtorrent resume blobs are the only recoverable representation of a partially
 * downloaded torrent — progress percentages alone cannot restore it after process death.
 * Checkpoints also create recovery markers consumed by [Recovery] on next startup.
 */
@Singleton
class ResumeDataPersister @Inject constructor(
    private val repository: DownloadRepository,
    private val torrentDao: com.pulldock.core.database.dao.TorrentTaskDao,
    private val recovery: Recovery,
    private val engine: TorrentEngine,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    /** Starts the periodic checkpoint loop; idempotent for the lifetime of the process. */
    fun start() {
        if (!jobStarted) {
            jobStarted = true
            scope.launch { checkpointLoop() }
        }
    }

    /** Persists one task immediately (e.g. after a pause request). */
    suspend fun checkpoint(taskId: String) {
        val task = repository.getTask(taskId) ?: return
        val engineId = task.engineTaskId ?: return
        when (val result = engine.exportResumeData(engineId)) {
            is TorrentResult.Success -> result.value?.let { blob ->
                torrentDao.updateResumeData(taskId, blob.bytes, blob.version)
                recovery.markInterrupted(taskId, "checkpoint", true)
            }
            is TorrentResult.Failure -> Unit // transient; next tick retries
        }
    }

    private suspend fun checkpointLoop() {
        while (scope.isActive) {
            delay(CHECKPOINT_INTERVAL_MS)
            val tasks = repository.observeQueue(QueueFilter.ALL).first()
                .filter { it.type == DownloadType.TORRENT && it.engineTaskId != null }
                .filter { it.state in DownloadStateMachine.activeStates }
            tasks.forEach { checkpoint(it.id) }
        }
    }

    private var jobStarted: Boolean = false

    private companion object {
        const val CHECKPOINT_INTERVAL_MS = 30_000L
    }
}
