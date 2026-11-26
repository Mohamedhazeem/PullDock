package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.StorageKind
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.extractor.model.ExtractorTaskDetails
import com.pulldock.domain.torrent.model.TorrentSource
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Use-case guards verified with in-memory fakes (constitution X: meaningful behavior,
 * including failure paths — not mocked-success-only).
 */
private class FakeLibraryForDedupe : com.pulldock.domain.download.repository.MediaLibraryRepository {
    override fun observe(query: com.pulldock.domain.download.repository.LibraryQuery) =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.pulldock.domain.download.repository.LibraryItem>())
    override suspend fun get(itemId: String) = null
    override suspend fun findBySourceHash(hash: String) = null
    override suspend fun reconcile() = com.pulldock.domain.download.repository.ReconcileReport(0, 0, 0)
    override suspend fun delete(itemId: String, deleteFile: Boolean) = Unit
}

class QueueUseCasesTest {

    private val destination = StorageDestination(StorageKind.APP_SPECIFIC, null, "App storage", null)

    // ---- Fake repository ----------------------------------------------------------

    private inner class FakeRepository : com.pulldock.domain.download.repository.DownloadRepository {
        val tasks = mutableMapOf<String, DownloadTask>()
        val stateCalls = mutableListOf<Pair<String, DownloadState>>()
        var nextTransitionRejects: Boolean = false

        override fun observeTask(id: String) = kotlinx.coroutines.flow.MutableStateFlow(tasks[id])
        override fun observeQueue(filter: com.pulldock.domain.download.repository.QueueFilter) =
            kotlinx.coroutines.flow.MutableStateFlow(tasks.values.toList())

        override fun observeTorrentDetails(taskId: String) = kotlinx.coroutines.flow.MutableStateFlow<TorrentTaskDetails?>(null)
        override fun observeExtractorDetails(taskId: String) = kotlinx.coroutines.flow.MutableStateFlow<ExtractorTaskDetails?>(null)

        override suspend fun getTask(id: String): DownloadTask? = tasks[id]
        override suspend fun getTorrentDetails(taskId: String): TorrentTaskDetails? = null
        override suspend fun getExtractorDetails(taskId: String): ExtractorTaskDetails? = null

        override suspend fun addTorrent(source: TorrentSource, destination: StorageDestination, groupId: String?): DownloadTask {
            val task = minimalTask(source.value, DownloadType.TORRENT)
            tasks[task.id] = task
            return task
        }

        override suspend fun addMedia(
            descriptor: com.pulldock.domain.extractor.model.MediaDescriptor,
            format: com.pulldock.domain.extractor.model.MediaFormat,
            destination: StorageDestination,
            groupId: String?,
        ): DownloadTask {
            val task = minimalTask(descriptor.sourceUrl, DownloadType.EXTRACTOR)
            tasks[task.id] = task
            return task
        }

        override suspend fun createGroup(type: DownloadType, title: String, source: String?) =
            com.pulldock.domain.download.model.DownloadGroup(
                id = "g${tasks.size + 1}",
                type = type,
                title = title,
                source = source,
                thumbnailUri = null,
                createdAt = Instant.EPOCH,
            )

        override fun observeGroups() =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.pulldock.domain.download.model.DownloadGroup>())

        override suspend fun getTasksByGroup(groupId: String) = emptyList<DownloadTask>()
        override suspend fun deleteGroup(groupId: String, deleteFiles: Boolean) {}

        override suspend fun updateState(id: String, to: DownloadState, error: DownloadError?): Boolean {
            if (nextTransitionRejects) return false
            stateCalls.add(id to to)
            tasks[id]?.let { tasks[id] = it.copy(state = to) }
            return true
        }

        override suspend fun updateProgress(id: String, progress: DownloadProgress) {}
        override suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?) {}
        override suspend fun reorder(orderedIds: List<String>) {}
        override suspend fun removeTask(id: String, deleteFile: Boolean) {
            tasks.remove(id)
        }

        override suspend fun findBySourceHash(hash: String): DownloadTask? =
            tasks.values.firstOrNull { it.sourceHash == hash }

        override suspend fun updateEngineTaskId(id: String, engineTaskId: String?) {}
        override suspend fun updateLimits(id: String, limits: TransferLimits) {}
    }

    private inner class FakeCoordinator(private val repo: FakeRepository) : QueueCoordinator {
        override val state = kotlinx.coroutines.flow.MutableStateFlow(
            com.pulldock.domain.download.queue.QueueState(emptyList(), 0, 0, 3, emptyMap()),
        )
        val paused = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val retried = mutableListOf<String>()
        val removed = mutableListOf<Pair<String, Boolean>>()

        override suspend fun enqueue(task: DownloadTask) {
            repo.updateState(task.id, DownloadState.QUEUED)
        }

        override suspend fun pause(id: String) {
            paused.add(id)
            repo.updateState(id, DownloadState.PAUSING)
        }

        override suspend fun resume(id: String) {
            resumed.add(id)
            repo.updateState(id, DownloadState.QUEUED)
        }

        override suspend fun cancel(id: String) {
            cancelled.add(id)
            repo.updateState(id, DownloadState.CANCELLED)
        }

        override suspend fun retry(id: String) {
            retried.add(id)
            repo.updateState(id, DownloadState.QUEUED)
        }

        override suspend fun move(ids: List<String>) {}

        override suspend fun remove(id: String, deleteFile: Boolean) {
            removed.add(id to deleteFile)
        }

        override suspend fun recover() = com.pulldock.domain.download.queue.RecoveryReport(0, 0, 0)

        override suspend fun onEngineEvent(
            taskId: String,
            sequence: Long,
            progressBytes: Long?,
            totalBytes: Long?,
            speed: Long?,
            state: DownloadState?,
            error: DownloadError?,
        ) {}
    }

    private fun minimalTask(source: String, type: DownloadType): DownloadTask = DownloadTask(
        id = "task-${minimalTaskCounter++}",
        type = type,
        source = source,
        sourceHash = com.pulldock.domain.download.model.SourceHashing.forTorrent(
                if (type == DownloadType.TORRENT) TorrentSource(TorrentSource.Kind.MAGNET, source) else TorrentSource(TorrentSource.Kind.MAGNET, source),
            ),
        displayName = "name",
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
        destination = destination,
        limits = TransferLimits.NONE,
        priority = 0,
        queueOrder = 0,
        engineTaskId = null,
    )

    private var minimalTaskCounter = 0

    // ---- Magnet validation --------------------------------------------------------

    @Test
    fun `valid hex magnet passes validation and creates a queued task`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val addTorrent = AddTorrent(repo, coordinator, FakeLibraryForDedupe())

        val magnet = "magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567&dn=test"
        val result = addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, magnet), destination)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(DownloadState.QUEUED, (result as UseCaseResult.Success).value.state)
    }

    @Test
    fun `valid base32 magnet is accepted`() = runTest {
        val result = AddTorrent(FakeRepository(), FakeCoordinator(FakeRepository()), FakeLibraryForDedupe())
            .invoke(TorrentSource(TorrentSource.Kind.MAGNET, "magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"), destination)
        assertTrue(result is UseCaseResult.Success)
    }

    @Test
    fun `malformed magnet is rejected with typed error and no task`() = runTest {
        val repo = FakeRepository()
        val addTorrent = AddTorrent(repo, FakeCoordinator(repo), FakeLibraryForDedupe())

        val bad = listOf(
            "",
            "http://example.com/file.torrent",
            "magnet:?xt=urn:btih:short",
            "magnet:?dn=only-name",
            "magnet:?xt=urn:sha1:0123456789ABCDEF0123456789ABCDEF01234567",
        )
        bad.forEach { value ->
            val result = addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, value), destination)
            assertTrue("Expected failure for '$value'", result is UseCaseResult.Failure)
            val error = (result as UseCaseResult.Failure).error
            assertTrue(error is DownloadError.Input)
        }
        assertTrue(repo.tasks.isEmpty())
    }

    @Test
    fun `duplicate magnet for an active task is rejected`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val addTorrent = AddTorrent(repo, coordinator, FakeLibraryForDedupe())
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"

        val first = addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, magnet), destination)
        assertTrue(first is UseCaseResult.Success)

        val second = addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, magnet.uppercase()), destination)
        assertTrue(second is UseCaseResult.Failure)
        assertEquals(
            DownloadError.TorrentEngine(DownloadError.TorrentReason.DUPLICATE_INFOHASH),
            (second as UseCaseResult.Failure).error,
        )
        assertEquals(1, repo.tasks.size)
    }

    @Test
    fun `same magnet after terminal state is allowed as a new task`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val addTorrent = AddTorrent(repo, coordinator, FakeLibraryForDedupe())
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"

        val first = (addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, magnet), destination) as UseCaseResult.Success).value
        repo.tasks[first.id] = first.copy(state = DownloadState.CANCELLED)

        val second = addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, magnet), destination)
        assertTrue(second is UseCaseResult.Success)
        assertEquals(2, repo.tasks.size)
    }

    // ---- Transfer control guards --------------------------------------------------

    @Test
    fun `start on completed task is rejected (FR-025)`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val task = minimalTask("src", DownloadType.TORRENT)
        repo.tasks[task.id] = task.copy(state = DownloadState.COMPLETED)

        val result = StartDownload(repo, coordinator).invoke(task.id)

        assertTrue(result is UseCaseResult.Failure)
        assertTrue(coordinator.resumed.isEmpty() && coordinator.retried.isEmpty())
    }

    @Test
    fun `start on paused task resumes`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val task = minimalTask("src", DownloadType.TORRENT)
        repo.tasks[task.id] = task.copy(state = DownloadState.PAUSED)

        val result = StartDownload(repo, coordinator).invoke(task.id)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(listOf(task.id), coordinator.resumed)
    }

    @Test
    fun `start on failed task retries`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val task = minimalTask("src", DownloadType.TORRENT)
        repo.tasks[task.id] = task.copy(state = DownloadState.FAILED)

        val result = StartDownload(repo, coordinator).invoke(task.id)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(listOf(task.id), coordinator.retried)
    }

    @Test
    fun `start on running task is an idempotent no-op (FR-026)`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val task = minimalTask("src", DownloadType.TORRENT)
        repo.tasks[task.id] = task.copy(state = DownloadState.RUNNING)

        val result = StartDownload(repo, coordinator).invoke(task.id)

        assertTrue(result is UseCaseResult.Success)
        assertTrue(coordinator.resumed.isEmpty() && coordinator.retried.isEmpty())
        assertEquals(DownloadState.RUNNING, repo.tasks[task.id]?.state)
    }

    @Test
    fun `pause on paused task is a no-op (FR-026)`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val task = minimalTask("src", DownloadType.TORRENT)
        repo.tasks[task.id] = task.copy(state = DownloadState.PAUSED)

        val result = PauseDownload(repo, coordinator).invoke(task.id)

        assertTrue(result is UseCaseResult.Success)
        assertTrue(coordinator.paused.isEmpty())
    }

    @Test
    fun `pause on running task transitions through pausing`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val task = minimalTask("src", DownloadType.TORRENT)
        repo.tasks[task.id] = task.copy(state = DownloadState.RUNNING)

        val result = PauseDownload(repo, coordinator).invoke(task.id)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(listOf(task.id), coordinator.paused)
    }

    @Test
    fun `retry is only legal from FAILED`() = runTest {
        val repo = FakeRepository()
        val coordinator = FakeCoordinator(repo)
        val task = minimalTask("src", DownloadType.TORRENT)
        repo.tasks[task.id] = task.copy(state = DownloadState.RUNNING)

        val result = RetryDownload(repo, coordinator).invoke(task.id)

        assertTrue(result is UseCaseResult.Failure)
        assertTrue(coordinator.retried.isEmpty())
    }
}
