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
import com.pulldock.domain.extractor.ExtractorEvent
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentEvent
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
    private val detailsRecorder: TorrentDetailsRecorder,
    private val completionFinalizer: CompletionFinalizer,
    private val resumeDataPersister: ResumeDataPersister,
    private val batteryMonitor: com.pulldock.core.network.BatteryMonitor,
    private val dispatchers: AppDispatchers,
) : QueueCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val queueState = MutableStateFlow(QueueState(emptyList(), 0, 0, 3, emptyMap()))

    override val state: StateFlow<QueueState> = queueState.asStateFlow()

    private val eventReconciler = EventReconciler(repository)

    /** Single-flight scheduling guard: ticks never overlap. */
    private val ticking = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Monotonic event sequence (T104) and engine-id → task-id resolution cache. */
    private val eventSequence = java.util.concurrent.atomic.AtomicLong(0)
    private val engineToTask = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val uploadedBytes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val currentPeerCount = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val currentSeedCount = java.util.concurrent.ConcurrentHashMap<String, Int>()
    /** Last (uploadedBytes, timestamp) used to derive upload rate for the UI. */
    private val lastUploadSample = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()
    private val metadataAttempted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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
        scope.launch { observeTorrentEvents() }
        scope.launch { observeExtractorEvents() }
        resumeDataPersister.start()
    }

    override suspend fun enqueue(task: DownloadTask) {
        // New tasks enter the scheduling queue as QUEUED (state-machine §2.2):
        // without this transition nothing would ever dispatch them.
        repository.updateState(task.id, DownloadState.QUEUED)
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
            resumeDataPersister.checkpoint(task.id)
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

    /**
     * Bridges the engine's hot event stream into the persisted queue (T104). Without
     * this collector, progress/state alerts never reach the database and a task that
     * is genuinely downloading still appears idle in the UI.
     */
    private suspend fun observeTorrentEvents() {
        torrentEngine.observeEvents(null).collect { event ->
            val engineTaskId = event.taskId()
            val taskId = engineToTask[engineTaskId]
                ?: repository.observeQueue(QueueFilter.ALL).first().firstOrNull { it.engineTaskId == engineTaskId }?.id
                ?: return@collect
            engineToTask[engineTaskId] = taskId
            when (event) {
                is TorrentEvent.MetadataAvailable ->
                    detailsRecorder.recordMetadata(taskId, event.metadata)
                is TorrentEvent.PeerCount -> {
                    currentPeerCount[engineTaskId] = event.peers
                    currentSeedCount[engineTaskId] = event.seeds
                    detailsRecorder.updateStats(taskId, event.peers, event.seeds, uploadedBytes.getOrDefault(engineTaskId, 0L))
                }
                is TorrentEvent.SeedingStats -> {
                    uploadedBytes[engineTaskId] = event.uploadedBytes
                    detailsRecorder.updateStats(taskId, currentPeerCount.getOrDefault(engineTaskId, 0), currentSeedCount.getOrDefault(engineTaskId, 0), event.uploadedBytes)
                }
                is TorrentEvent.Progress -> {
                    // Alert-order fallback: metadata may arrive before the handle cache
                    // is readable. Fetch once without blocking the event collector.
                    if (metadataAttempted.add(engineTaskId)) {
                        scope.launch {
                            val task = repository.getTask(taskId) ?: return@launch
                            if (repository.getTorrentDetails(taskId)?.files.isNullOrEmpty()) {
                                when (val meta = torrentEngine.fetchMetadata(engineTaskId, java.time.Duration.ofSeconds(3))) {
                                    is TorrentResult.Success -> detailsRecorder.recordMetadata(taskId, meta.value)
                                    is TorrentResult.Failure -> Unit
                                }
                            }
                        }
                    }
                    val now = System.currentTimeMillis()
                    val previous = lastUploadSample[engineTaskId]
                    if (previous != null && now > previous.second) {
                        val rate = (event.uploadedBytes - previous.first) * 1000 / (now - previous.second)
                        detailsRecorder.updateUploadSpeed(taskId, rate.coerceAtLeast(0))
                    }
                    lastUploadSample[engineTaskId] = event.uploadedBytes to now
                    onEngineEvent(
                        taskId = taskId,
                        sequence = eventSequence.incrementAndGet(),
                        progressBytes = event.downloadedBytes,
                        totalBytes = event.totalBytes,
                        speed = event.speedBytesPerSec,
                        state = null,
                        error = null,
                    )
                }
                is TorrentEvent.StateChanged -> {
                    onEngineEvent(
                        taskId = taskId,
                        sequence = eventSequence.incrementAndGet(),
                        progressBytes = null,
                        totalBytes = null,
                        speed = null,
                        state = event.state,
                        error = null,
                    )
                    // FR-030: verification and finalization precede COMPLETED.
                    if (event.state == DownloadState.VERIFYING) completionFinalizer.finalize(taskId)
                }
                is TorrentEvent.Error -> onEngineEvent(
                    taskId = taskId,
                    sequence = eventSequence.incrementAndGet(),
                    progressBytes = null,
                    totalBytes = null,
                    speed = null,
                    state = DownloadState.FAILED,
                    error = event.error,
                )
                else -> Unit // peer counts/seeding stats have no persisted field yet
            }
        }
    }

    /** Bridges media-extractor events into the persisted queue and completion flow. */
    private suspend fun observeExtractorEvents() {
        extractor.observeEvents(null).collect { event ->
            val taskId = engineToTask[event.taskId()]
                ?: repository.observeQueue(QueueFilter.ALL).first()
                    .firstOrNull { it.engineTaskId == event.taskId() }?.id
                ?: return@collect
            when (event) {
                is ExtractorEvent.Progress -> {
                    val total = event.totalBytes ?: repository.getTask(taskId)?.totalBytes
                    onEngineEvent(
                        taskId = taskId,
                        sequence = eventSequence.incrementAndGet(),
                        progressBytes = event.downloadedBytes,
                        totalBytes = total,
                        speed = event.speedBytesPerSec,
                        state = null,
                        error = null,
                    )
                }
                is ExtractorEvent.StateChanged -> onEngineEvent(
                    taskId = taskId,
                    sequence = eventSequence.incrementAndGet(),
                    progressBytes = null,
                    totalBytes = null,
                    speed = null,
                    state = event.state,
                    error = null,
                )
                is ExtractorEvent.Error -> onEngineEvent(
                    taskId = taskId,
                    sequence = eventSequence.incrementAndGet(),
                    progressBytes = null,
                    totalBytes = null,
                    speed = null,
                    state = DownloadState.FAILED,
                    error = event.error,
                )
                is ExtractorEvent.Processing -> Unit // completion is handled by the download result
            }
        }
    }

    private fun ExtractorEvent.taskId(): String = when (this) {
        is ExtractorEvent.Progress -> taskId
        is ExtractorEvent.Processing -> taskId
        is ExtractorEvent.StateChanged -> taskId
        is ExtractorEvent.Error -> taskId
    }

    private fun TorrentEvent.taskId(): String = when (this) {
        is TorrentEvent.MetadataAvailable -> taskId
        is TorrentEvent.Progress -> taskId
        is TorrentEvent.PeerCount -> taskId
        is TorrentEvent.PieceVerified -> taskId
        is TorrentEvent.StateChanged -> taskId
        is TorrentEvent.Error -> taskId
        is TorrentEvent.SeedingStats -> taskId
    }

    /** Applies a single-flight scheduling tick: retry promotion → policy → dispatch. */
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
        val engineId = task.engineTaskId ?: when (val added = torrentEngine.addTorrent(task.asTorrentSource())) {
            is TorrentResult.Success -> {
                repository.updateEngineTaskId(task.id, added.value)
                engineToTask[added.value] = task.id
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
        // Register the engine task id before the first progress event. The extractor
        // emits progress as soon as bytes land; without this mapping every event is
        // dropped and the UI shows an endless indeterminate bar.
        val engineTaskId = "${details.providerId.name}-${details.originalUrl.hashCode()}"
        repository.updateEngineTaskId(task.id, engineTaskId)
        engineToTask[engineTaskId] = task.id
        repository.updateState(task.id, DownloadState.RUNNING)

        // The extractor download is long-running. Do not block the scheduling loop:
        // run it in the coordinator scope and reconcile the result when it completes.
        scope.launch {
            val result = extractor.download(descriptor, format)
            engineToTask.remove(engineTaskId)
            when (result) {
                is ExtractorResult.Success -> {
                    val path = result.value
                    engineToTask[path] = task.id
                    repository.updateEngineTaskId(task.id, path)
                    val finalized = completionFinalizer.finalizeMedia(task.id, path)
                    if (finalized == null) {
                        repository.getTask(task.id)?.let { current ->
                            if (current.state != DownloadState.FAILED) {
                                val error = DownloadError.Storage(
                                    DownloadError.StorageReason.FINALIZE_FAILED,
                                    "output unavailable",
                                )
                                repository.updateState(task.id, DownloadState.FAILED, error)
                                retryCoordinator.onFailure(
                                    current.copy(state = DownloadState.FAILED),
                                    error,
                                    policyRepository.current(),
                                )
                            }
                        }
                    }
                }
                is ExtractorResult.Failure -> {
                    repository.getTask(task.id)?.let { failed ->
                        repository.updateState(task.id, DownloadState.FAILED, result.error)
                        retryCoordinator.onFailure(
                            failed.copy(state = DownloadState.FAILED),
                            result.error,
                            policyRepository.current(),
                        )
                    }
                }
            }
        }
        return null
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
