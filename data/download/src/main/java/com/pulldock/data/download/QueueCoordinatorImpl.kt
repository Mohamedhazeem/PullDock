package com.pulldock.data.download

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.queue.QueueState
import com.pulldock.domain.download.queue.RecoveryReport
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.NetworkPolicyRepository
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.state.DownloadStateMachine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single queue coordinator for both engine types (ADR-0004). Slice 1 delivers the
 * persistence-backed state machine skeleton: enqueue/pause/resume/cancel/retry/remove
 * drive persisted state transitions, scheduling and concurrency enforcement land in
 * Slice 5 (T100–T104) where engines exist.
 *
 * Every persisted transition goes through [DownloadRepository.updateState], so an
 * illegal transition can never reach the database (invariant §3.3); queue snapshots
 * are derived from the repository's hot flows.
 */
@Singleton
class QueueCoordinatorImpl @Inject constructor(
    private val repository: DownloadRepository,
    private val policyRepository: NetworkPolicyRepository,
    private val dispatchers: AppDispatchers,
) : QueueCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val queueState = MutableStateFlow(QueueState(emptyList(), 0, 0, 3, emptyMap()))

    override val state: StateFlow<QueueState> = queueState.asStateFlow()

    init {
        // Keep the hot snapshot in sync with persisted state; conflation bounds the
        // update rate (plan.md §Performance Design) and avoids unbounded buffering.
        scope.launch {
            kotlinx.coroutines.flow.combine(
                repository.observeQueue(QueueFilter.ALL),
                policyRepository.observe(),
            ) { tasks, policy -> tasks to policy }
                .collect { (tasks, policy) ->
                    queueState.value = tasks.toQueueState(policy)
                }
        }
    }

    override suspend fun enqueue(task: DownloadTask) {
        // CREATED -> VALIDATING happens in the pipeline slices; enqueue places the task
        // in the queue ordering and emits a fresh snapshot.
        repository.reorder(listOf(task.id))
    }

    override suspend fun pause(id: String) {
        applyTransition(id, DownloadState.PAUSING)
    }

    override suspend fun resume(id: String) {
        applyTransition(id, DownloadState.QUEUED)
    }

    override suspend fun cancel(id: String) {
        applyTransition(id, DownloadState.CANCELLED)
    }

    override suspend fun retry(id: String) {
        val task = repository.getTask(id) ?: return
        if (task.state != DownloadState.FAILED) return
        repository.updateRetry(id, task.retryCount + 1, null)
        applyTransition(id, DownloadState.QUEUED)
    }

    override suspend fun move(ids: List<String>) {
        repository.reorder(ids)
    }

    override suspend fun remove(id: String, deleteFile: Boolean) {
        val task = repository.getTask(id) ?: return
        if (task.state == DownloadState.REMOVING) return
        if (DownloadStateMachine.transition(task.state, DownloadState.REMOVING) == null) return
        repository.updateState(id, DownloadState.REMOVING)
        repository.removeTask(id, deleteFile)
    }

    override suspend fun recover(): RecoveryReport {
        // Engine-aware recovery lands in Slice 5 (T107–T110); the skeleton reports
        // nothing recovered rather than pretending work happened.
        return RecoveryReport(resumed = 0, needsUserAction = 0, failed = 0)
    }

    override suspend fun onEngineEvent(
        taskId: String,
        sequence: Long,
        progressBytes: Long?,
        totalBytes: Long?,
        speed: Long?,
        state: DownloadState?,
        error: DownloadError?,
    ) {
        // Event reconciliation is T104 (Slice 5); the skeleton applies only state
        // changes through the guarded repository path.
        state?.let { applyTransition(taskId, it, error) }
    }

    /** Applies a legal transition or records a typed failure; never throws to callers. */
    private suspend fun applyTransition(id: String, to: DownloadState, error: DownloadError? = null) {
        val task = repository.getTask(id) ?: return
        if (DownloadStateMachine.transition(task.state, to) == null) return
        repository.updateState(id, to, error)
    }

    private fun List<DownloadTask>.toQueueState(policy: DownloadPolicy): QueueState = QueueState(
        tasks = this,
        activeCount = count { it.state in DownloadStateMachine.activeStates },
        queuedCount = count { it.state == DownloadState.QUEUED || it.state == DownloadState.RETRY_WAIT },
        globalLimit = policy.maxConcurrent,
        perEngineLimits = mapOf(
            DownloadType.TORRENT to policy.torrentMaxConcurrent,
            DownloadType.EXTRACTOR to policy.extractorMaxConcurrent,
        ),
    )
}
