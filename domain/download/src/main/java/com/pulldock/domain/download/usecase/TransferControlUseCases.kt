package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.state.DownloadStateMachine
import javax.inject.Inject

/**
 * Transfer-control use cases (FR-007/FR-025/FR-026). Every action guards against the
 * state machine: completed tasks cannot resume, cancelled tasks never silently
 * restart, pause is idempotent, and duplicate starts cannot create second sessions.
 */
class StartDownload @Inject constructor(
    private val repository: DownloadRepository,
    private val coordinator: QueueCoordinator,
) {
    /** Starts or resumes from `QUEUED`/`PAUSED`; retries a `FAILED` task. */
    suspend operator fun invoke(taskId: String): UseCaseResult<Unit> {
        val task = repository.getTask(taskId)
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.DB_ERROR))
        return when (task.state) {
            DownloadState.PAUSED -> coordinator.resume(taskId).let { UseCaseResult.Success(Unit) }
            DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED, DownloadState.NEEDS_PERMISSION ->
                coordinator.retry(taskId).let { UseCaseResult.Success(Unit) }
            // Already scheduled/running: repeated starts are a no-op (FR-026).
            DownloadState.QUEUED, DownloadState.STARTING, DownloadState.RUNNING, DownloadState.PAUSING,
            DownloadState.RETRY_WAIT, DownloadState.VERIFYING, DownloadState.POST_PROCESSING,
            -> UseCaseResult.Success(Unit)
            else -> UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        }
    }
}

/** Idempotent pause (FR-026). Pausing a paused task is a no-op. */
class PauseDownload @Inject constructor(
    private val repository: DownloadRepository,
    private val coordinator: QueueCoordinator,
) {
    suspend operator fun invoke(taskId: String): UseCaseResult<Unit> {
        val task = repository.getTask(taskId)
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.DB_ERROR))
        if (task.state == DownloadState.PAUSED) return UseCaseResult.Success(Unit)
        if (!DownloadStateMachine.canPause(task.state)) {
            return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        }
        coordinator.pause(taskId)
        return UseCaseResult.Success(Unit)
    }
}

/** Idempotent resume into the queue (US4 scenario 3). */
class ResumeDownload @Inject constructor(
    private val repository: DownloadRepository,
    private val coordinator: QueueCoordinator,
) {
    suspend operator fun invoke(taskId: String): UseCaseResult<Unit> {
        val task = repository.getTask(taskId)
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.DB_ERROR))
        if (!DownloadStateMachine.canEnterQueue(task.state)) {
            return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        }
        coordinator.resume(taskId)
        return UseCaseResult.Success(Unit)
    }
}

/** Cooperative cancel; idempotent and safe under concurrent requests (invariant §3.6). */
class CancelDownload @Inject constructor(
    private val coordinator: QueueCoordinator,
) {
    suspend operator fun invoke(taskId: String): UseCaseResult<Unit> {
        coordinator.cancel(taskId)
        return UseCaseResult.Success(Unit)
    }
}

/** Bounded retry with persisted backoff (US4 scenario 4); only `FAILED` tasks retry. */
class RetryDownload @Inject constructor(
    private val repository: DownloadRepository,
    private val coordinator: QueueCoordinator,
) {
    suspend operator fun invoke(taskId: String): UseCaseResult<Unit> {
        val task = repository.getTask(taskId)
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.DB_ERROR))
        if (task.state != DownloadState.FAILED) {
            return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        }
        coordinator.retry(taskId)
        return UseCaseResult.Success(Unit)
    }
}

/**
 * Removes the task record; [deleteFile] controls the distinct file deletion (FR-027).
 * Both paths require user confirmation upstream (FR-041).
 */
class RemoveDownload @Inject constructor(
    private val coordinator: QueueCoordinator,
) {
    suspend operator fun invoke(taskId: String, deleteFile: Boolean): UseCaseResult<Unit> {
        coordinator.remove(taskId, deleteFile)
        return UseCaseResult.Success(Unit)
    }
}

/**
 * Removes a group and all its children. Active children are cancelled first, then the
 * group row, child tasks, library items, and (when [deleteFiles]) files are deleted.
 */
class RemoveGroup @Inject constructor(
    private val repository: DownloadRepository,
    private val coordinator: QueueCoordinator,
) {
    suspend operator fun invoke(groupId: String, deleteFiles: Boolean): UseCaseResult<Unit> {
        repository.getTasksByGroup(groupId).forEach { coordinator.cancel(it.id) }
        repository.deleteGroup(groupId, deleteFiles)
        return UseCaseResult.Success(Unit)
    }
}
