package com.pulldock.data.torrent

import android.content.Context
import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.repository.StoragePreferencesRepository
import com.pulldock.domain.torrent.EngineTaskId
import com.pulldock.domain.torrent.StreamingHandle
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentEvent
import com.pulldock.domain.torrent.TorrentMetadata
import com.pulldock.domain.torrent.TorrentResult
import com.pulldock.domain.torrent.model.FileSelection
import com.pulldock.domain.torrent.model.ResumeBlob
import com.pulldock.domain.torrent.model.SeedingPolicy
import com.pulldock.domain.torrent.model.TorrentFile
import com.pulldock.domain.torrent.model.TorrentSource
import com.pulldock.engines.torrent.AlertLoop
import com.pulldock.engines.torrent.EngineObservation
import com.pulldock.engines.torrent.LibtorrentSession
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.Priority
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo

/**
 * libtorrent4j adapter implementing the domain [TorrentEngine] contract (T061,
 * ADR-0001). Every engine call is marshalled onto the session thread
 * ([LibtorrentSession.execute]) and results are posted back to the caller; alerts
 * arrive through the [AlertLoop]; progress is polled at ~2 Hz for the UI
 * (plan.md §Torrent Architecture).
 *
 * Engine task ids are lowercase infohash hex — stable across process death, which
 * makes duplicate detection (FR-006) and recovery (T071) addressable without parsing
 * opaque handles. Ingestion (magnet, .torrent, resume blob) goes through one uniform
 * `async_add_torrent` path; input validation happens upstream in the `AddTorrent` use
 * case before the engine sees the source, and `parseMagnetUri` failures map to typed
 * errors as defense in depth.
 *
 * App-specific storage only (FR-036); resolved destinations arrive with the storage
 * slice (T150).
 */
@Singleton
class TorrentEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: LibtorrentSession,
    private val dispatchers: AppDispatchers,
    private val storagePreferences: StoragePreferencesRepository,
) : TorrentEngine {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val events = MutableSharedFlow<TorrentEvent>(extraBufferCapacity = 256)

    private val handles = ConcurrentHashMap<EngineTaskId, TorrentHandle>()
    private val finishedAt = ConcurrentHashMap<EngineTaskId, Instant>()
    private val seedingPolicies = ConcurrentHashMap<EngineTaskId, SeedingPolicy>()
    private val resumeData = ConcurrentHashMap<EngineTaskId, ResumeBlob>()
    private val metadataWaiters = ConcurrentHashMap<EngineTaskId, () -> Unit>()
    private val listenerRegistered = AtomicBoolean(false)

    private val alertLoop = AlertLoop(::onObservation)

    private fun ensureSession() {
        session.ensureStarted()
        if (listenerRegistered.compareAndSet(false, true)) {
            session.addListener(alertLoop)
            scope.launch { pollStatus() }
        }
    }

    // ---- Ingestion ----------------------------------------------------------------

    override suspend fun addTorrent(source: TorrentSource): TorrentResult<EngineTaskId> {
        ensureSession()
        val savePath = defaultSavePath()
        val result = session.execute {
            try {
                when (source.kind) {
                    TorrentSource.Kind.MAGNET -> {
                        val params = AddTorrentParams.parseMagnetUri(source.value)
                        addParams(params, savePath)
                    }
                    TorrentSource.Kind.TORRENT_URI -> {
                        val bytes = readTorrentBytes(source.value)
                            ?: return@execute TorrentResult.Failure(
                                DownloadError.Input(DownloadError.InputReason.MALFORMED_TORRENT_FILE),
                            )
                        val params = AddTorrentParams()
                        params.torrentInfo = TorrentInfo.bdecode(bytes)
                        addParams(params, savePath)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TorrentResult.Failure(ErrorMapper.mapThrowable(e))
            }
        }.get()
        return result
    }

    /** Uniform ingestion; duplicate infohashes resolve to the existing id (FR-006). */
    private fun addParams(params: AddTorrentParams, savePath: String): TorrentResult<EngineTaskId> {
        params.savePath = savePath
        val infoHashHex = params.infoHashes.v1.toHex().lowercase()
        session.manager().find(Sha1Hash.parseHex(infoHashHex))?.let {
            return TorrentResult.Success(it.infoHash().toHex().lowercase())
        }
        session.manager().swig().async_add_torrent(params.swig())
        val handle = waitForHandle(infoHashHex, ADD_WAIT_MS)
            ?: return TorrentResult.Failure(
                DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT, "handle not confirmed"),
            )
        handles[handle.infoHash().toHex().lowercase()] = handle
        return TorrentResult.Success(handle.infoHash().toHex().lowercase())
    }

    override suspend fun fetchMetadata(taskId: EngineTaskId, timeout: Duration): TorrentResult<TorrentMetadata> {
        ensureSession()
        val handle = session.execute { findHandle(taskId) }.get()
            ?: return TorrentResult.Failure(DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT))
        return try {
            withTimeout(timeout.toMillis()) {
                suspendCancellableCoroutine { continuation ->
                    handle.torrentFile()?.let { info ->
                        continuation.resumeWith(Result.success(TorrentResult.Success(toMetadata(info))))
                        return@suspendCancellableCoroutine
                    }
                    metadataWaiters[taskId] = {
                        val metadata = handle.torrentFile()?.let(::toMetadata)
                        if (metadata != null) {
                            continuation.resumeWith(Result.success(TorrentResult.Success(metadata)))
                        } else {
                            continuation.resumeWith(
                                Result.success(
                                    TorrentResult.Failure(
                                        DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT),
                                    ),
                                ),
                            )
                        }
                    }
                    continuation.invokeOnCancellation { metadataWaiters.remove(taskId) }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            metadataWaiters.remove(taskId)
            TorrentResult.Failure(DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT))
        }
    }

    // ---- Transfer control ------------------------------------------------------------

    override suspend fun start(taskId: EngineTaskId): TorrentResult<Unit> {
        ensureSession()
        val found = session.execute { findHandle(taskId)?.also { it.resume() } != null }.get()
        return if (found) {
            emitState(taskId, DownloadState.RUNNING)
            TorrentResult.Success(Unit)
        } else {
            TorrentResult.Failure(notFound())
        }
    }

    override suspend fun pause(taskId: EngineTaskId): TorrentResult<Unit> {
        ensureSession()
        val paused = session.execute {
            // Idempotent: a missing handle is treated as already paused (FR-026).
            findHandle(taskId)?.also { it.pause() } != null
        }.get()
        return if (paused) {
            emitState(taskId, DownloadState.PAUSED)
            TorrentResult.Success(Unit)
        } else {
            TorrentResult.Success(Unit)
        }
    }

    override suspend fun resume(taskId: EngineTaskId): TorrentResult<Unit> = start(taskId)

    override suspend fun recheck(taskId: EngineTaskId): TorrentResult<Unit> {
        ensureSession()
        val rechecked = session.execute { findHandle(taskId)?.also { it.swig().force_recheck() } != null }.get()
        return if (rechecked) TorrentResult.Success(Unit) else TorrentResult.Failure(notFound())
    }

    // ---- Selection and limits ----------------------------------------------------------

    override suspend fun selectFiles(taskId: EngineTaskId, selection: FileSelection): TorrentResult<Unit> {
        ensureSession()
        val result = session.execute {
            val handle = findHandle(taskId)
                ?: return@execute TorrentResult.Failure(notFound())
            val info = handle.torrentFile()
                ?: return@execute TorrentResult.Failure(
                    DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT),
                )
            val priorities = Array(info.numFiles()) { index ->
                if (index in selection.selectedIndexes) {
                    selection.priorityByIndex[index]?.let(::priorityOf) ?: Priority.DEFAULT
                } else {
                    Priority.IGNORE
                }
            }
            handle.prioritizeFiles(priorities)
            TorrentResult.Success(Unit)
        }.get()
        return result
    }

    override suspend fun setFilePriority(taskId: EngineTaskId, priorities: Map<Int, Int>): TorrentResult<Unit> {
        ensureSession()
        return session.execute {
            val handle = findHandle(taskId)
                ?: return@execute TorrentResult.Failure(notFound())
            val info = handle.torrentFile()
                ?: return@execute TorrentResult.Failure(
                    DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT),
                )
            priorities.forEach { (index, priority) ->
                if (index in 0 until info.numFiles()) {
                    handle.filePriority(index, priorityOf(priority))
                }
            }
            TorrentResult.Success(Unit)
        }.get()
    }

    override suspend fun setLimits(taskId: EngineTaskId, limits: TransferLimits): TorrentResult<Unit> {
        ensureSession()
        return session.execute {
            val handle = findHandle(taskId)
                ?: return@execute TorrentResult.Failure(notFound())
            handle.downloadLimit = limits.downloadRateBytesPerSec?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
            handle.uploadLimit = limits.uploadRateBytesPerSec?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
            TorrentResult.Success(Unit)
        }.get()
    }

    override suspend fun setSequential(taskId: EngineTaskId, enabled: Boolean): TorrentResult<Unit> {
        ensureSession()
        val sequential = org.libtorrent4j.swig.libtorrent.getSequential_download()
        return session.execute {
            val handle = findHandle(taskId)
                ?: return@execute TorrentResult.Failure(notFound())
            if (enabled) {
                handle.setFlags(sequential)
            } else {
                handle.unsetFlags(sequential)
            }
            TorrentResult.Success(Unit)
        }.get()
    }

    override suspend fun setSeedingPolicy(taskId: EngineTaskId, policy: SeedingPolicy): TorrentResult<Unit> {
        ensureSession()
        seedingPolicies[taskId] = policy
        return session.execute {
            val handle = findHandle(taskId)
                ?: return@execute TorrentResult.Failure(notFound())
            policy.maxUploadRateBytesPerSec?.let { rate ->
                handle.uploadLimit = rate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            // Seed-after-completion off + already finished: stop now (FR-012).
            if (!policy.seedAfterCompletion && finishedAt.containsKey(taskId)) {
                handle.pause()
            }
            TorrentResult.Success(Unit)
        }.get()
    }

    // ---- Removal and recovery ------------------------------------------------------------

    override suspend fun remove(taskId: EngineTaskId): TorrentResult<Unit> {
        ensureSession()
        val removed = session.execute {
            findHandle(taskId)?.also { session.manager().remove(it) } != null // files kept (FR-027)
        }.get()
        handles.remove(taskId)
        finishedAt.remove(taskId)
        seedingPolicies.remove(taskId)
        resumeData.remove(taskId)
        emitState(taskId, DownloadState.REMOVING)
        return if (removed) TorrentResult.Success(Unit) else TorrentResult.Success(Unit)
    }

    override suspend fun exportResumeData(taskId: EngineTaskId): TorrentResult<ResumeBlob?> {
        ensureSession()
        val handle = session.execute { findHandle(taskId) }.get()
            ?: return TorrentResult.Failure(notFound())
        session.execute { handle.saveResumeData() }
        // Bytes arrive on the SaveResumeDataAlert path; bounded wait, null when absent.
        val deadline = System.currentTimeMillis() + RESUME_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            resumeData[taskId]?.let { blob ->
                if (ResumeData.isUsable(blob)) return TorrentResult.Success(blob)
            }
            delay(100)
        }
        return TorrentResult.Success(null)
    }

    override suspend fun importResumeData(taskId: EngineTaskId, blob: ResumeBlob): TorrentResult<Unit> {
        ensureSession()
        if (!ResumeData.isUsable(blob)) {
            return TorrentResult.Failure(DownloadError.TorrentEngine(DownloadError.TorrentReason.INVALID_RESUME_DATA))
        }
        val savePath = defaultSavePath()
        return session.execute {
            try {
                val swigParams = org.libtorrent4j.swig.libtorrent.read_resume_data_ex(
                    org.libtorrent4j.swig.byte_vector(blob.bytes),
                    org.libtorrent4j.swig.error_code(),
                )
                swigParams.save_path = savePath
                session.manager().swig().async_add_torrent(swigParams)
                waitForHandle(taskId, ADD_WAIT_MS)?.let { handles[taskId] = it }
                TorrentResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TorrentResult.Failure(ErrorMapper.mapThrowable(e))
            }
        }.get()
    }

    // ---- Streaming (US6; real loopback bridge lands in Slice 6, T159) ----------------------

    override suspend fun requestStreamingBridge(taskId: EngineTaskId, fileIndex: Int): TorrentResult<StreamingHandle> =
        TorrentResult.Failure(DownloadError.TorrentEngine(DownloadError.TorrentReason.STREAMING_UNAVAILABLE))

    // ---- Observation and lifecycle -----------------------------------------------------------

    override fun observeEvents(taskId: EngineTaskId?): Flow<TorrentEvent> = events

    override fun cancel(taskId: EngineTaskId) {
        metadataWaiters.remove(taskId)
    }

    override suspend fun close() {
        scope.cancel()
        session.removeListener(alertLoop)
        listenerRegistered.set(false)
        handles.clear()
        session.close()
    }

    // ---- Internals -----------------------------------------------------------------------------

    private fun onObservation(observation: EngineObservation) {
        scope.launch {
            when (observation) {
                is EngineObservation.Added -> emitState(observation.infoHashHex, DownloadState.STARTING)
                is EngineObservation.MetadataReady -> {
                    metadataWaiters.remove(observation.infoHashHex)?.invoke()
                    // Persist the real name/size/files once available (FR-003); the
                    // untrusted name is sanitized by the data-layer recorder.
                    val metadata = findHandle(observation.infoHashHex)?.torrentFile()?.let(::toMetadata)
                    if (metadata != null) {
                        emit(
                            observation.infoHashHex,
                            TorrentEvent.MetadataAvailable(observation.infoHashHex, metadata),
                        )
                    }
                    emitState(observation.infoHashHex, DownloadState.AWAITING_FILE_SELECTION)
                }
                is EngineObservation.MetadataFailed ->
                    emit(observation.infoHashHex, TorrentEvent.Error(observation.infoHashHex, ErrorMapper.fromObservation(observation)))
                is EngineObservation.Finished -> {
                    finishedAt[observation.infoHashHex] = Instant.now()
                    emitState(observation.infoHashHex, DownloadState.VERIFYING)
                }
                is EngineObservation.Failed ->
                    emit(observation.infoHashHex, TorrentEvent.Error(observation.infoHashHex, ErrorMapper.fromObservation(observation)))
                is EngineObservation.FileFailed ->
                    emit(observation.infoHashHex, TorrentEvent.Error(observation.infoHashHex, ErrorMapper.fromObservation(observation)))
                is EngineObservation.ResumeDataReady ->
                    resumeData[observation.infoHashHex] = ResumeBlob(version = observation.version, bytes = observation.bytes)
                is EngineObservation.Removed -> emitState(observation.infoHashHex, DownloadState.REMOVING)
            }
        }
    }

    /** ~2 Hz status poll driving Progress/PeerCount events (throttled UI emission). */
    private suspend fun pollStatus() {
        while (scope.isActive) {
            delay(POLL_INTERVAL_MS)
            if (!session.isRunning) continue
            handles.forEach { (taskId, handle) ->
                try {
                    val status = session.execute { handle.status() }.get() ?: return@forEach
                    val total = status.totalWanted()
                    val done = status.totalWantedDone()
                    if (total > 0) {
                        emit(
                            taskId,
                            TorrentEvent.Progress(
                                taskId = taskId,
                                downloadedBytes = done,
                                totalBytes = total,
                                speedBytesPerSec = status.downloadPayloadRate().toLong(),
                                uploadedBytes = status.allTimeUpload(),
                            ),
                        )
                    }
                    emit(
                        taskId,
                        TorrentEvent.PeerCount(
                            taskId = taskId,
                            peers = status.numPeers(),
                            seeds = status.numSeeds(),
                        ),
                    )
                    applySeedingStopConditions(taskId)
                } catch (_: Exception) {
                    // Session shutting down or handle gone; close() cancels this loop.
                }
            }
        }
    }

    /** Enforces stop-ratio/time-limit so seeding never runs indefinitely (FR-012). */
    private suspend fun applySeedingStopConditions(taskId: EngineTaskId) {
        val policy = seedingPolicies[taskId] ?: return
        val finished = finishedAt[taskId] ?: return
        val handle = handles[taskId] ?: return
        val status = session.execute { handle.status() }.get() ?: return
        val total = status.totalWanted()
        val ratio = if (total > 0) status.allTimeUpload().toDouble() / total else 0.0
        emit(taskId, TorrentEvent.SeedingStats(taskId, status.allTimeUpload(), ratio))
        val elapsed = Duration.between(finished, Instant.now()).seconds
        val stopRatio = policy.stopRatio
        val timeLimit = policy.timeLimitSeconds
        val ratioReached = stopRatio != null && ratio >= stopRatio
        val timeReached = timeLimit != null && elapsed >= timeLimit
        if (!policy.seedAfterCompletion || ratioReached || timeReached) {
            session.execute { handle.pause() }
            emitState(taskId, DownloadState.PAUSED)
        }
    }

    private suspend fun emit(taskId: EngineTaskId, event: TorrentEvent) {
        events.emit(event)
    }

    private suspend fun emitState(taskId: EngineTaskId, state: DownloadState) {
        events.emit(TorrentEvent.StateChanged(taskId, state))
    }

    private fun findHandle(taskId: EngineTaskId): TorrentHandle? =
        handles[taskId] ?: session.manager().find(Sha1Hash.parseHex(taskId))

    private fun waitForHandle(infoHashHex: String, timeoutMs: Long): TorrentHandle? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            session.manager().find(Sha1Hash.parseHex(infoHashHex))?.let { return it }
            Thread.sleep(50)
        }
        return null
    }

    private fun toMetadata(info: TorrentInfo): TorrentMetadata {
        val storage = info.files()
        val files = (0 until info.numFiles()).map { index ->
            TorrentFile(
                index = index,
                path = storage.filePath(index),
                sizeBytes = storage.fileSize(index),
                priority = Priority.DEFAULT.swig().toInt(),
                selected = true,
            )
        }
        return TorrentMetadata(
            name = info.name(),
            infohash = info.infoHash().toHex().lowercase(),
            totalSizeBytes = info.totalSize(),
            files = files,
            trackers = emptyList(),
        )
    }

    private fun priorityOf(value: Int): Priority = when {
        value <= 0 -> Priority.IGNORE
        value >= 7 -> Priority.TOP_PRIORITY
        else -> Priority.fromSwig(value)
    }

    private fun notFound(): DownloadError =
        DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT, "unknown engine task")

    private suspend fun defaultSavePath(): String {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val folder = storagePreferences.currentDownloadFolder()
        if (folder.isNullOrBlank()) return root.absolutePath
        val target = java.io.File(root, folder)
        return if (target.exists() || target.mkdirs()) target.absolutePath else root.absolutePath
    }

    /** Reads a picked `.torrent` document; SAF content URIs go through ContentResolver. */
    private fun readTorrentBytes(uri: String): ByteArray? = try {
        when {
            uri.startsWith("content://") ->
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var read: Int
                    var total = 0
                    while (input.read(chunk).also { read = it } != -1) {
                        total += read
                        if (total > MAX_TORRENT_BYTES) return@use null
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
            uri.startsWith("file://") ->
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(java.net.URI(uri)))
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val ADD_WAIT_MS = 5_000L
        private const val RESUME_WAIT_MS = 3_000L
        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_TORRENT_BYTES = 8 * 1024 * 1024
    }
}
