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
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.ProcessingStatus
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentResult
import com.pulldock.domain.torrent.model.TorrentSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single queue coordinator for both engine types (T100, ADR-0004, FR-028). A
 * scheduling tick runs on the data scope and, in order:
 *
 * 1. promotes due RETRY_WAIT tasks back into the queue (T102);
 * 2. pauses active tasks violating network/battery policy (T106, US7);
 * 3. starts queued tasks within the global and per-engine caps with round-robin
 *    fairness between engine types (T100);
 * 4. applies engine events idempotently (T104).
 *
 * Every persisted transition flows through the guarded repository path, so an illegal
 * transition can never reach the database (invariant §3.3). Engine failures route to
 * the retry coordinator for persisted, bounded backoff (US4 scenario 4).
 */
@Singleton
class QueueCoordinatorImpl @Inject constructor(
    private val repository: DownloadRepository,
    private val policyRepository: NetworkPolicyRepository,
    private val torrentEngine: TorrentEngine,
    private val extractor: MediaExtractor,
    private val retryCoordinator: RetryCoordinator,
    private val policyEnforcer: PolicyEnforcer,
    private val batteryMonitor: com.pulldock.core.network.BatteryMonitor,
    private val dispatchers: AppDispatchers,
) : QueueCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val queueState = MutableStateFlow(QueueState(emptyList(), 0, 0, 3, emptyMap()))

    override val state: StateFlow<QueueState> = queueState.asStateFlow()

    private val eventReconciler = EventReconciler(repository)

    /** Single-flight scheduling guard: ticks never overlap. */
    private val ticking = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        scope.launch {
            combine(
                repository.observeQueue(QueueFilter.ALL),
                policyRepository.observe(),
            ) { tasks, policy -> tasks to policy }
                .collect { (tasks, policy) ->
                    queueState.value = tasks.toQueueState(policy)
                }
        }
        scope.launch { schedulingLoop() }
    }

    override suspend fun enqueue(task: DownloadTask) {
        repository.reorder(listOf(task.id))
    }

    override suspend fun pause(id: String) {
        val task = repository.getTask(id) ?: return
        if (task.state == DownloadState.PAUSED) return // idempotent (FR-026)
        if (!DownloadStateMachine.canPause(task.state)) return
        repository.updateState(id, DownloadState.PAUSING)
        val engineId = task.engineTaskId
        if (task.type == DownloadType.TORRENT && engineId != null) {
            torrentEngine.pause(engineId)
        }
        if (task.type == DownloadType.EXTRACTOR && engineId != null) {
            extractor.cancel(engineId)
        }
        repository.updateState(id, DownloadState.PAUSED)
    }

    override suspend fun resume(id: String) {
        applyTransition(id, DownloadState.QUEUED)
    }

    override suspend fun cancel(id: String) {
        val task = repository.getTask(id) ?: return
        if (DownloadStateMachine.transition(task.state, DownloadState.CANCELLED) == null) return
        val engineId = task.engineTaskId
        when (task.type) {
            DownloadType.TORRENT -> torrentEngine.cancel(engineId ?: task.id)
            DownloadType.EXTRACTOR -> extractor.cancel(engineId ?: task.id)
        }
        repository.updateState(id, DownloadState.CANCELLED)
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
        if (DownloadStateMachine.transition(task.state, DownloadState.REMOVING) == null) return
        val engineId = task.engineTaskId
        if (engineId != null) {
            when (task.type) {
                DownloadType.TORRENT -> torrentEngine.remove(engineId)
                DownloadType.EXTRACTOR -> extractor.cancel(engineId)
            }
        }
        repository.updateState(id, DownloadState.REMOVING)
        repository.removeTask(id, deleteFile)
    }

    override suspend fun recover(): RecoveryReport {
        // Full recovery orchestration (T110/T130) calls Recovery.recover() from the
        // startup path; the coordinator exposes the same report shape here.
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
        val applied = eventReconciler.onEngineEvent(taskId, sequence, state, progressBytes, totalBytes, speed)
        if (error != null && state == DownloadState.FAILED) {
            repository.getTask(taskId)?.let { task ->
                retryCoordinator.onFailure(task, error, policyRepository.current())
            }
        }
        if (error != null && !applied) {
            // Even an out-of-order failure must not vanish; record it if legal.
            repository.getTask(taskId)?.let { task ->
                if (DownloadStateMachine.canTransition(task.state, DownloadState.FAILED)) {
                    repository.updateState(taskId, DownloadState.FAILED, error)
                    retryCoordinator.onFailure(task, error, policyRepository.current())
                }
            }
        }
    }

    /** Scheduling tick: retry promotion → policy enforcement → dispatch. */
    private suspend fun schedulingLoop() {
        while (scope.isActive) {
            if (ticking.compareAndSet(false, true)) {
                try {
                    val tasks = repository.observeQueue(QueueFilter.ALL).first()
                    val policy = policyRepository.current()

                    retryCoordinator.promoteDue(tasks)
                    policyEnforcer.pauseViolating(tasks, policy, batteryPercent())

                    val canRun = policyEnforcer.networkAllows(policy) &&
                        policyEnforcer.batteryAllows(policy, batteryPercent())
                    if (canRun) {
                        QueueScheduler.readyToStart(tasks, policy).forEach { task ->
                            startTask(task)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A failed tick must not kill the loop; the next tick retries.
                } finally {
                    ticking.set(false)
                }
            }
            delay(TICK_INTERVAL_MS)
        }
    }

    /** Starts one dispatched task on its engine; failures route to the retry path. */
    private suspend fun startTask(task: DownloadTask) {
        if (repository.updateState(task.id, DownloadState.STARTING)) {
            val result = when (task.type) {
                DownloadType.TORRENT -> startTorrent(task)
                DownloadType.EXTRACTOR -> startExtractor(task)
            }
            if (result != null) {
                repository.getTask(task.id)?.let { failedTask ->
                    repository.updateState(task.id, DownloadState.FAILED, result)
                    retryCoordinator.onFailure(failedTask.copy(state = DownloadState.FAILED), result, policyRepository.current())
                }
            }
        }
    }

    private suspend fun startTorrent(task: DownloadTask): DownloadError? {
        val engineId = task.engineTaskId ?: when (val added = torrentEngine.addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, task.source))) {
            is TorrentResult.Success -> {
                repository.updateEngineTaskId(task.id, added.value)
                added.value
            }
            is TorrentResult.Failure -> return added.error
        }
        return when (val started = torrentEngine.start(engineId)) {
            is TorrentResult.Success -> {
                repository.updateState(task.id, DownloadState.RUNNING)
                null
            }
            is TorrentResult.Failure -> started.error
        }
    }

    private suspend fun startExtractor(task: DownloadTask): DownloadError? {
        val details = repository.getExtractorDetails(task.id)
            ?: return DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID)
        val format = details.selectedFormat
            ?: return DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "no format selected")
        val descriptor = MediaDescriptor(
            sourceUrl = details.originalUrl,
            providerId = details.providerId,
            title = task.displayName,
            thumbnailUrl = null,
            durationSeconds = null,
            authorizedContext = null,
        )
        return when (val result = extractor.download(descriptor, format)) {
            is com.pulldock.domain.extractor.ExtractorResult.Success -> {
                repository.updateEngineTaskId(task.id, result.value)
                repository.updateState(task.id, DownloadState.RUNNING)
                null
            }
            is com.pulldock.domain.extractor.ExtractorResult.Failure -> result.error
        }
    }

    /** Battery level for the threshold check (sticky-intent read, T106). */
    private fun batteryPercent(): Int = batteryMonitor.currentPercent() ?: Int.MAX_VALUE

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

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
    }
}
