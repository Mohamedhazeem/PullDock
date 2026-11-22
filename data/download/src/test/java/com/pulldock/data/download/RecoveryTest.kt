package com.pulldock.data.download

import com.pulldock.core.database.dao.RecoveryMarkerDao
import com.pulldock.core.database.entity.RecoveryMarkerEntity
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.StorageKind
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentResult
import com.pulldock.domain.torrent.model.ResumeBlob
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recovery behavior (T073, US9): resumable tasks restore, unrecoverable ones surface
 * NEEDS_USER_ACTION — never silent drops.
 */
class RecoveryTest {

    private class FakeMarkerDao : RecoveryMarkerDao {
        val markers = mutableMapOf<String, RecoveryMarkerEntity>()
        override suspend fun upsert(marker: RecoveryMarkerEntity) {
            markers[marker.taskId] = marker
        }
        override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(markers.values.toList())
        override suspend fun getByTaskId(taskId: String): RecoveryMarkerEntity? = markers[taskId]
        override suspend fun getRecoverable(): List<RecoveryMarkerEntity> =
            markers.values.filter { it.recoverable }

        override suspend fun incrementAttempts(taskId: String): Int {
            val marker = markers[taskId] ?: return 0
            val updated = marker.copy(attempts = marker.attempts + 1)
            markers[taskId] = updated
            return updated.attempts
        }
        override suspend fun delete(taskId: String) {
            markers.remove(taskId)
        }
    }

    private class FakeRepository : DownloadRepository {
        val tasks = mutableMapOf<String, DownloadTask>()
        val states = mutableListOf<Pair<String, DownloadState>>()
        override fun observeTask(id: String) = kotlinx.coroutines.flow.MutableStateFlow(tasks[id])
        override fun observeQueue(filter: com.pulldock.domain.download.repository.QueueFilter) =
            kotlinx.coroutines.flow.MutableStateFlow(tasks.values.toList())
        override fun observeTorrentDetails(taskId: String) =
            kotlinx.coroutines.flow.MutableStateFlow<com.pulldock.domain.torrent.model.TorrentTaskDetails?>(null)
        override fun observeExtractorDetails(taskId: String) =
            kotlinx.coroutines.flow.MutableStateFlow<com.pulldock.domain.extractor.model.ExtractorTaskDetails?>(null)
        override suspend fun getTask(id: String): DownloadTask? = tasks[id]
        override suspend fun getTorrentDetails(taskId: String) =
            if (tasks[taskId]?.type == DownloadType.TORRENT) {
                com.pulldock.domain.torrent.model.TorrentTaskDetails(
                    taskId = taskId,
                    infohash = "e-$taskId",
                    name = "name",
                    totalSizeBytes = 100,
                    files = emptyList(),
                    trackers = emptyList(),
                    peerCount = 0,
                    sequentialDownload = false,
                    resumeData = com.pulldock.domain.torrent.model.ResumeBlob(
                        version = com.pulldock.data.torrent.ResumeData.CURRENT_VERSION,
                        bytes = byteArrayOf(1, 2, 3),
                    ),
                    seedingPolicy = com.pulldock.domain.torrent.model.SeedingPolicy.DEFAULT,
                    uploadedBytes = 0,
                    state = null,
                )
            } else {
                null
            }
        override suspend fun getExtractorDetails(taskId: String) = null
        override suspend fun addTorrent(source: com.pulldock.domain.torrent.model.TorrentSource, destination: StorageDestination) = error("unused")
        override suspend fun addMedia(
            descriptor: com.pulldock.domain.extractor.model.MediaDescriptor,
            format: com.pulldock.domain.extractor.model.MediaFormat,
            destination: StorageDestination,
        ) = error("unused")
        override suspend fun updateState(id: String, to: DownloadState, error: com.pulldock.core.model.DownloadError?): Boolean {
            tasks[id]?.let { tasks[id] = it.copy(state = to) }
            states.add(id to to)
            return true
        }
        override suspend fun updateProgress(id: String, progress: DownloadProgress) {}
        override suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?) {}
        override suspend fun reorder(orderedIds: List<String>) {}
        override suspend fun removeTask(id: String, deleteFile: Boolean) {}
        override suspend fun findBySourceHash(hash: String): DownloadTask? = null
        override suspend fun updateEngineTaskId(id: String, engineTaskId: String?) {}
        override suspend fun updateLimits(id: String, limits: TransferLimits) {}
    }

    private class StubEngine(private val importSucceeds: Boolean) : TorrentEngine {
        var imported: ResumeBlob? = null
        override suspend fun addTorrent(source: com.pulldock.domain.torrent.model.TorrentSource) = error("unused")
        override suspend fun fetchMetadata(taskId: String, timeout: java.time.Duration) = error("unused")
        override suspend fun selectFiles(taskId: String, selection: com.pulldock.domain.torrent.model.FileSelection) = error("unused")
        override suspend fun setSequential(taskId: String, enabled: Boolean) = error("unused")
        override suspend fun start(taskId: String) = error("unused")
        override suspend fun pause(taskId: String) = error("unused")
        override suspend fun resume(taskId: String) = error("unused")
        override suspend fun recheck(taskId: String) = error("unused")
        override suspend fun setFilePriority(taskId: String, priorities: Map<Int, Int>) = error("unused")
        override suspend fun setLimits(taskId: String, limits: TransferLimits) = error("unused")
        override suspend fun setSeedingPolicy(taskId: String, policy: com.pulldock.domain.torrent.model.SeedingPolicy) = error("unused")
        override suspend fun remove(taskId: String) = error("unused")
        override suspend fun exportResumeData(taskId: String) = error("unused")
        override suspend fun importResumeData(taskId: String, blob: ResumeBlob): TorrentResult<Unit> {
            imported = blob
            return if (importSucceeds) TorrentResult.Success(Unit) else TorrentResult.Failure(
                com.pulldock.core.model.DownloadError.TorrentEngine(com.pulldock.core.model.DownloadError.TorrentReason.INVALID_RESUME_DATA),
            )
        }
        override suspend fun requestStreamingBridge(taskId: String, fileIndex: Int) = error("unused")
        override fun observeEvents(taskId: String?) = kotlinx.coroutines.flow.MutableStateFlow<com.pulldock.domain.torrent.TorrentEvent>(
            com.pulldock.domain.torrent.TorrentEvent.StateChanged("x", DownloadState.QUEUED),
        )
        override fun cancel(taskId: String) = Unit
        override suspend fun close() = Unit
    }

    private fun task(id: String, type: DownloadType): DownloadTask = DownloadTask(
        id = id,
        type = type,
        source = "src-$id",
        sourceHash = "hash-$id",
        displayName = "Task $id",
        outputFilename = "",
        state = DownloadState.CREATED,
        progress = DownloadProgress.IDLE,
        totalBytes = null,
        downloadedBytes = 0,
        downloadSpeed = 0,
        uploadSpeed = null,
        etaSeconds = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        completedAt = null,
        outputUri = null,
        errorCode = null,
        errorDetails = null,
        retryCount = 0,
        nextRetryAt = null,
        destination = StorageDestination(StorageKind.APP_SPECIFIC, null, "App", null),
        limits = TransferLimits.NONE,
        priority = 0,
        queueOrder = 0,
        engineTaskId = "e-$id",
    )

    @Test
    fun `recoverable torrent with usable resume data restores to queued`() = runTest {
        val repo = FakeRepository()
        repo.tasks["t1"] = task("t1", DownloadType.TORRENT)
        val markers = FakeMarkerDao()
        markers.upsert(
            RecoveryMarkerEntity("t1", interruptedAt = 1, reason = "PROCESS_DEATH", resumeDataPresent = true, recoverable = true, attempts = 0),
        )
        val recovery = Recovery(repo, markers, StubEngine(importSucceeds = true))

        val report = recovery.recover()

        assertEquals(1, report.resumed)
        assertEquals(0, report.needsUserAction)
        assertEquals(DownloadState.QUEUED, repo.tasks["t1"]?.state)
    }

    @Test
    fun `unreconstructable engine state marks NEEDS_USER_ACTION, never drops (US9 scenario 2)`() = runTest {
        val repo = FakeRepository()
        repo.tasks["t1"] = task("t1", DownloadType.TORRENT)
        val markers = FakeMarkerDao()
        markers.upsert(
            RecoveryMarkerEntity("t1", interruptedAt = 1, reason = "PROCESS_DEATH", resumeDataPresent = true, recoverable = true, attempts = 0),
        )
        val recovery = Recovery(repo, markers, StubEngine(importSucceeds = false))

        val report = recovery.recover()

        assertEquals(0, report.resumed)
        assertEquals(1, report.needsUserAction)
        assertEquals(DownloadState.NEEDS_USER_ACTION, repo.tasks["t1"]?.state)
    }

    @Test
    fun `extractor tasks re-enter the queue without engine state`() = runTest {
        val repo = FakeRepository()
        repo.tasks["m1"] = task("m1", DownloadType.EXTRACTOR)
        val markers = FakeMarkerDao()
        markers.upsert(
            RecoveryMarkerEntity("m1", interruptedAt = 1, reason = "PROCESS_DEATH", resumeDataPresent = false, recoverable = true, attempts = 0),
        )
        val engine = StubEngine(importSucceeds = false)
        val recovery = Recovery(repo, markers, engine)

        val report = recovery.recover()

        assertEquals(1, report.resumed)
        assertEquals(DownloadState.QUEUED, repo.tasks["m1"]?.state)
        assertTrue(engine.imported == null)
    }
}
