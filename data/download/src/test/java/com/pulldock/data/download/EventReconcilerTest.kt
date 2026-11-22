package com.pulldock.data.download

import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.StorageKind
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Event reconciliation tests (T104/T113): duplicate and out-of-order engine events
 * never regress persisted state; progress only moves forward; totals never shrink.
 */
class EventReconcilerTest {

    private class Repo(initial: DownloadTask) : com.pulldock.domain.download.repository.DownloadRepository {
        var task: DownloadTask = initial
        val stateCalls = mutableListOf<DownloadState>()
        var progress: DownloadProgress? = null

        override fun observeTask(id: String) = kotlinx.coroutines.flow.MutableStateFlow(task)
        override fun observeQueue(filter: com.pulldock.domain.download.repository.QueueFilter) =
            kotlinx.coroutines.flow.MutableStateFlow(listOf(task))

        override fun observeTorrentDetails(taskId: String) =
            kotlinx.coroutines.flow.MutableStateFlow<com.pulldock.domain.torrent.model.TorrentTaskDetails?>(null)

        override fun observeExtractorDetails(taskId: String) =
            kotlinx.coroutines.flow.MutableStateFlow<com.pulldock.domain.extractor.model.ExtractorTaskDetails?>(null)

        override suspend fun getTask(id: String): DownloadTask = task
        override suspend fun getTorrentDetails(taskId: String) = null
        override suspend fun getExtractorDetails(taskId: String) = null
        override suspend fun addTorrent(source: com.pulldock.domain.torrent.model.TorrentSource, destination: StorageDestination) = error("unused")
        override suspend fun addMedia(
            descriptor: com.pulldock.domain.extractor.model.MediaDescriptor,
            format: com.pulldock.domain.extractor.model.MediaFormat,
            destination: StorageDestination,
        ) = error("unused")

        override suspend fun updateState(id: String, to: DownloadState, error: com.pulldock.core.model.DownloadError?): Boolean {
            stateCalls.add(to)
            task = task.copy(state = to)
            return true
        }

        override suspend fun updateProgress(id: String, progress: DownloadProgress) {
            this.progress = progress
            task = task.copy(downloadedBytes = progress.downloadedBytes, totalBytes = progress.totalBytes)
        }

        override suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?) {}
        override suspend fun reorder(orderedIds: List<String>) {}
        override suspend fun removeTask(id: String, deleteFile: Boolean) {}
        override suspend fun findBySourceHash(hash: String): DownloadTask? = null
        override suspend fun updateEngineTaskId(id: String, engineTaskId: String?) {}
        override suspend fun updateLimits(id: String, limits: TransferLimits) {}
    }

    private fun task(state: DownloadState = DownloadState.RUNNING, downloaded: Long = 0, total: Long? = 1000): DownloadTask = DownloadTask(
        id = "t1",
        type = DownloadType.TORRENT,
        source = "src",
        sourceHash = "hash",
        displayName = "Task",
        outputFilename = "",
        state = state,
        progress = DownloadProgress(downloaded, total, 0, null),
        totalBytes = total,
        downloadedBytes = downloaded,
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
        engineTaskId = "e",
    )

    @Test
    fun `duplicate sequence numbers are idempotent no-ops`() = runTest {
        val repo = Repo(task())
        val reconciler = EventReconciler(repo)
        val first = reconciler.onEngineEvent("t1", sequence = 5, state = null, downloadedBytes = 100, totalBytes = 1000, speed = 10)
        val duplicate = reconciler.onEngineEvent("t1", sequence = 5, state = null, downloadedBytes = 100, totalBytes = 1000, speed = 10)
        assertTrue(first)
        assertFalse(duplicate)
    }

    @Test
    fun `out-of-order events never regress state or progress`() = runTest {
        val repo = Repo(task())
        val reconciler = EventReconciler(repo)

        reconciler.onEngineEvent("t1", sequence = 3, state = null, downloadedBytes = 500, totalBytes = 1000, speed = 10)
        assertEquals(500L, repo.progress?.downloadedBytes)

        // A stale event (older sequence, lower bytes) is ignored.
        val stale = reconciler.onEngineEvent("t1", sequence = 2, state = null, downloadedBytes = 100, totalBytes = 1000, speed = 10)
        assertFalse(stale)
        assertEquals(500L, repo.progress?.downloadedBytes)
    }

    @Test
    fun `illegal state regressions are rejected by the state machine`() = runTest {
        val repo = Repo(task())
        val reconciler = EventReconciler(repo)
        // RUNNING -> QUEUED is illegal; the reconciler must not apply it.
        val applied = reconciler.onEngineEvent("t1", sequence = 1, state = DownloadState.QUEUED, downloadedBytes = null, totalBytes = null, speed = null)
        assertFalse(applied)
        assertTrue(repo.stateCalls.isEmpty())
    }

    @Test
    fun `forward transitions apply and totals never shrink`() = runTest {
        val repo = Repo(task())
        val reconciler = EventReconciler(repo)
        val applied = reconciler.onEngineEvent("t1", sequence = 4, state = DownloadState.PAUSING, downloadedBytes = 800, totalBytes = 900, speed = 5)
        assertTrue(applied)
        assertEquals(DownloadState.PAUSING, repo.stateCalls.last())
        // totalBytes=900 < known 1000: the known total is preserved (invariant §3.5).
        assertEquals(1000L, repo.progress?.totalBytes)
        assertEquals(800L, repo.progress?.downloadedBytes)
    }
}
