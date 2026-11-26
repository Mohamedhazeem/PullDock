package com.pulldock.feature.queue

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.StorageKind
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.usecase.CancelDownload
import com.pulldock.domain.download.usecase.ObserveDownloadQueue
import com.pulldock.domain.download.usecase.PauseDownload
import com.pulldock.domain.download.usecase.RemoveDownload
import com.pulldock.domain.download.usecase.ResumeDownload
import com.pulldock.domain.download.usecase.RetryDownload
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Queue ViewModel behavior with in-memory fakes (T054, US4): filters, per-task action
 * independence, removal confirmation gate, and typed action errors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())

    private class RecordingRepo(
        private val tasks: MutableStateFlow<List<DownloadTask>>,
    ) : DownloadRepository {
        override fun observeTask(id: String) = MutableStateFlow<DownloadTask?>(null)
        override fun observeQueue(filter: QueueFilter) = tasks
        override fun observeTorrentDetails(taskId: String) = MutableStateFlow<com.pulldock.domain.torrent.model.TorrentTaskDetails?>(null)
        override fun observeExtractorDetails(taskId: String) = MutableStateFlow<com.pulldock.domain.extractor.model.ExtractorTaskDetails?>(null)
        override suspend fun getTask(id: String): DownloadTask? = tasks.value.firstOrNull { it.id == id }
        override suspend fun getTorrentDetails(taskId: String) = null
        override suspend fun getExtractorDetails(taskId: String) = null
        override suspend fun addTorrent(
            source: com.pulldock.domain.torrent.model.TorrentSource,
            destination: StorageDestination,
            groupId: String?,
        ) = error("unused")

        override suspend fun addMedia(
            descriptor: com.pulldock.domain.extractor.model.MediaDescriptor,
            format: com.pulldock.domain.extractor.model.MediaFormat,
            destination: StorageDestination,
            groupId: String?,
        ) = error("unused")

        override suspend fun createGroup(type: DownloadType, title: String, source: String?) = error("unused")
        override fun observeGroups() = MutableStateFlow(emptyList<com.pulldock.domain.download.model.DownloadGroup>())
        override suspend fun getTasksByGroup(groupId: String) = emptyList<DownloadTask>()
        override suspend fun deleteGroup(groupId: String, deleteFiles: Boolean) = Unit

        override suspend fun updateState(id: String, to: DownloadState, error: DownloadError?): Boolean {
            tasks.value = tasks.value.map { if (it.id == id) it.copy(state = to) else it }
            return true
        }

        override suspend fun updateProgress(id: String, progress: DownloadProgress) {}
        override suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?) {}
        override suspend fun reorder(orderedIds: List<String>) {}
        override suspend fun removeTask(id: String, deleteFile: Boolean) {
            tasks.value = tasks.value.filterNot { it.id == id }
        }
        override suspend fun findBySourceHash(hash: String): DownloadTask? = null
        override suspend fun updateEngineTaskId(id: String, engineTaskId: String?) {}
        override suspend fun updateLimits(id: String, limits: TransferLimits) {}
    }

    private inner class UseCases {
        val repo = RecordingRepo(tasks)
        val coordinator = RecordingCoordinator()
        val observe = ObserveDownloadQueue(repo)
        val pauseUc = PauseDownload(repo, coordinator)
        val resumeUc = ResumeDownload(repo, coordinator)
        val cancelUc = CancelDownload(coordinator)
        val retryUc = RetryDownload(repo, coordinator)
        val removeUc = RemoveDownload(coordinator)
    }

    private class RecordingCoordinator : com.pulldock.domain.download.queue.QueueCoordinator {
        override val state = MutableStateFlow(com.pulldock.domain.download.queue.QueueState(emptyList(), 0, 0, 3, emptyMap()))
        var pauseTarget: String? = null
        var resumeTarget: String? = null
        var retryTarget: String? = null
        var cancelTarget: String? = null
        var removed: Pair<String, Boolean>? = null

        override suspend fun enqueue(task: DownloadTask) = Unit
        override suspend fun pause(id: String) {
            pauseTarget = id
        }

        override suspend fun resume(id: String) {
            resumeTarget = id
        }

        override suspend fun cancel(id: String) {
            cancelTarget = id
        }

        override suspend fun retry(id: String) {
            retryTarget = id
        }

        override suspend fun move(ids: List<String>) = Unit

        override suspend fun remove(id: String, deleteFile: Boolean) {
            removed = id to deleteFile
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
        ) = Unit
    }

    private fun task(id: String, state: DownloadState, type: DownloadType = DownloadType.TORRENT): DownloadTask = DownloadTask(
        id = id,
        type = type,
        source = "source-$id",
        sourceHash = "hash-$id",
        displayName = "Task $id",
        outputFilename = "",
        state = state,
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
        engineTaskId = null,
    )

    @Test
    fun `filters select the expected tasks`() = runTest(mainRule.testDispatcher) {
        val useCases = UseCases()
        tasks.value = listOf(
            task("a", DownloadState.RUNNING, DownloadType.TORRENT),
            task("b", DownloadState.QUEUED, DownloadType.EXTRACTOR),
            task("c", DownloadState.PAUSED, DownloadType.TORRENT),
            task("d", DownloadState.COMPLETED, DownloadType.EXTRACTOR),
            task("e", DownloadState.FAILED, DownloadType.TORRENT),
        )
        val vm = QueueViewModel(useCases.observe, useCases.pauseUc, useCases.resumeUc, useCases.cancelUc, useCases.retryUc, useCases.removeUc)
        // stateIn(WhileSubscribed) needs an active collector to produce values.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onFilterChanged(QueueFilter.ACTIVE)
        advanceUntilIdle()
        assertEquals(listOf("a"), vm.uiState.value.tasks.map { it.id })

        vm.onFilterChanged(QueueFilter.FAILED)
        advanceUntilIdle()
        assertEquals(listOf("e"), vm.uiState.value.tasks.map { it.id })

        vm.onFilterChanged(QueueFilter.ALL)
        advanceUntilIdle()
        assertEquals(5, vm.uiState.value.tasks.size)
    }

    @Test
    fun `per-task actions reach the use cases independently`() = runTest(mainRule.testDispatcher) {
        val useCases = UseCases()
        tasks.value = listOf(task("a", DownloadState.RUNNING), task("b", DownloadState.RUNNING))
        val vm = QueueViewModel(useCases.observe, useCases.pauseUc, useCases.resumeUc, useCases.cancelUc, useCases.retryUc, useCases.removeUc)
        // stateIn(WhileSubscribed) needs an active collector to produce values.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.pause("a")
        advanceUntilIdle()
        assertEquals("a", useCases.coordinator.pauseTarget)
        assertNull(useCases.coordinator.cancelTarget)
    }

    @Test
    fun `removal requires confirmation and carries the deleteFile decision`() = runTest(mainRule.testDispatcher) {
        val useCases = UseCases()
        tasks.value = listOf(task("a", DownloadState.RUNNING))
        val vm = QueueViewModel(useCases.observe, useCases.pauseUc, useCases.resumeUc, useCases.cancelUc, useCases.retryUc, useCases.removeUc)
        // stateIn(WhileSubscribed) needs an active collector to produce values.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.requestRemove("a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showingRemovalConfirmation)

        vm.dismissRemove()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showingRemovalConfirmation)
        assertNull(useCases.coordinator.removed)

        vm.requestRemove("a")
        advanceUntilIdle()
        vm.confirmRemove(deleteFile = false)
        advanceUntilIdle()
        assertEquals("a" to false, useCases.coordinator.removed)
        assertFalse(vm.uiState.value.showingRemovalConfirmation)
    }

    @Test
    fun `action on an unknown task surfaces a typed error and is consumable`() = runTest(mainRule.testDispatcher) {
        val useCases = UseCases()
        tasks.value = emptyList()
        val vm = QueueViewModel(useCases.observe, useCases.pauseUc, useCases.resumeUc, useCases.cancelUc, useCases.retryUc, useCases.removeUc)
        // stateIn(WhileSubscribed) needs an active collector to produce values.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.pause("missing")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.actionError is DownloadError.Persistence)

        vm.consumeActionError()
        advanceUntilIdle()
        assertNull(vm.uiState.value.actionError)
    }
}
