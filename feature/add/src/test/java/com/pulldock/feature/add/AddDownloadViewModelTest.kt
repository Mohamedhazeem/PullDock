package com.pulldock.feature.add

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.ProviderId
import com.pulldock.core.model.StorageKind
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.usecase.AddTorrent
import com.pulldock.domain.download.usecase.EnqueueDownload
import com.pulldock.domain.download.usecase.EnqueuePlaylist
import com.pulldock.domain.download.usecase.FetchAvailableFormats
import com.pulldock.domain.download.usecase.ResolveMediaUrl
import com.pulldock.domain.download.usecase.ResolvePlaylist
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.ProviderHealth
import com.pulldock.domain.torrent.model.TorrentSource
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Add-download flow behavior (T054, US2/US3 entry): classification, typed validation,
 * format selection, and honest enqueue. The fake extractor engine exercises the
 * provider registry contract without a runtime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class FakeLibraryForDedupe : com.pulldock.domain.download.repository.MediaLibraryRepository {
    override fun observe(query: com.pulldock.domain.download.repository.LibraryQuery) =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.pulldock.domain.download.repository.LibraryItem>())
    override suspend fun get(itemId: String) = null
    override suspend fun recordFromTask(taskId: String) = null
    override suspend fun findBySourceHash(hash: String) = null
    override suspend fun reconcile() = com.pulldock.domain.download.repository.ReconcileReport(0, 0, 0)
    override suspend fun delete(itemId: String, deleteFile: Boolean) = Unit
}

class AddDownloadViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeRepository : DownloadRepository {
        val tasks = mutableListOf<DownloadTask>()
        val groups = mutableListOf<com.pulldock.domain.download.model.DownloadGroup>()
        override fun observeTask(id: String) = MutableStateFlow<DownloadTask?>(null)
        override fun observeQueue(filter: com.pulldock.domain.download.repository.QueueFilter) = MutableStateFlow(tasks.toList())
        override fun observeTorrentDetails(taskId: String) = MutableStateFlow<com.pulldock.domain.torrent.model.TorrentTaskDetails?>(null)
        override fun observeExtractorDetails(taskId: String) = MutableStateFlow<com.pulldock.domain.extractor.model.ExtractorTaskDetails?>(null)
        override suspend fun getTask(id: String): DownloadTask? = tasks.firstOrNull { it.id == id }
        override suspend fun getTorrentDetails(taskId: String) = null
        override suspend fun getExtractorDetails(taskId: String) = null

        override suspend fun addTorrent(
            source: TorrentSource,
            destination: StorageDestination,
            groupId: String?,
        ): DownloadTask {
            val task = task(source.value, DownloadType.TORRENT, SourceHashing.forTorrent(source))
            tasks.add(task)
            return task
        }

        override suspend fun addMedia(
            descriptor: MediaDescriptor,
            format: MediaFormat,
            destination: StorageDestination,
            groupId: String?,
        ): DownloadTask {
            val task = task(descriptor.sourceUrl, DownloadType.EXTRACTOR, SourceHashing.forMedia(descriptor.sourceUrl))
            tasks.add(task)
            return task
        }

        override suspend fun createGroup(
            type: DownloadType,
            title: String,
            source: String?,
        ) = com.pulldock.domain.download.model.DownloadGroup(
            id = "g-${groups.size + 1}",
            type = type,
            title = title,
            source = source,
            thumbnailUri = null,
            createdAt = Instant.EPOCH,
        )

        override fun observeGroups() = MutableStateFlow(emptyList<com.pulldock.domain.download.model.DownloadGroup>())

        override suspend fun getGroup(id: String) = null

        override suspend fun getTasksByGroup(groupId: String) = emptyList<DownloadTask>()

        override suspend fun deleteGroup(groupId: String, deleteFiles: Boolean) {}

        override suspend fun updateState(id: String, to: DownloadState, error: DownloadError?): Boolean = true
        override suspend fun updateProgress(id: String, progress: DownloadProgress) {}
        override suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?) {}
        override suspend fun reorder(orderedIds: List<String>) {}
        override suspend fun removeTask(id: String, deleteFile: Boolean) {}
        override suspend fun findBySourceHash(hash: String): DownloadTask? = tasks.firstOrNull { it.sourceHash == hash }
        override suspend fun updateEngineTaskId(id: String, engineTaskId: String?) {}
        override suspend fun updateLimits(id: String, limits: TransferLimits) {}

        private fun task(source: String, type: DownloadType, hash: String): DownloadTask = DownloadTask(
            id = "t-${tasks.size + 1}",
            type = type,
            source = source,
            sourceHash = hash,
            displayName = "Task",
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
            engineTaskId = null,
        )
    }

    private object SourceHashing {
        fun forTorrent(source: TorrentSource): String = "hash-torrent-${source.value.lowercase()}"
        fun forMedia(url: String): String = "hash-url-${url.trim().lowercase()}"
    }

    private class RecordingCoordinator : QueueCoordinator {
        override val state = MutableStateFlow(com.pulldock.domain.download.queue.QueueState(emptyList(), 0, 0, 3, emptyMap()))
        val enqueued = mutableListOf<String>()
        override suspend fun enqueue(task: DownloadTask) {
            enqueued.add(task.id)
        }
        override suspend fun pause(id: String) = Unit
        override suspend fun resume(id: String) = Unit
        override suspend fun cancel(id: String) = Unit
        override suspend fun retry(id: String) = Unit
        override suspend fun move(ids: List<String>) = Unit
        override suspend fun remove(id: String, deleteFile: Boolean) = Unit
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

    /** Fake extractor: URL known, runtime incompatible (mirrors the bundled-runtime rule). */
    private class FakeExtractor(private val incompatible: Boolean) : MediaExtractor {
        override suspend fun resolve(url: String): ExtractorResult<MediaDescriptor> =
            if (incompatible) {
                ExtractorResult.Failure(DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE))
            } else {
                ExtractorResult.Success(
                    MediaDescriptor(
                        sourceUrl = url,
                        providerId = ProviderId.YOUTUBE,
                        title = "Fixture video",
                        thumbnailUrl = null,
                        durationSeconds = 90L,
                        authorizedContext = "user-owned upload",
                    ),
                )
            }

        override suspend fun availableFormats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>> =
            ExtractorResult.Success(
                listOf(
                    MediaFormat("f1", "1080p", "mp4", "avc1", "mp4a", 10_000_000L, true, 30L),
                    MediaFormat("f2", null, "m4a", null, "mp4a", 1_000_000L, false, null),
                ),
            )

        override suspend fun download(descriptor: MediaDescriptor, format: MediaFormat): ExtractorResult<String> =
            ExtractorResult.Success("et-1")

        override fun observeEvents(taskId: String?) = MutableStateFlow<com.pulldock.domain.extractor.ExtractorEvent>(
            com.pulldock.domain.extractor.ExtractorEvent.StateChanged("et-1", DownloadState.QUEUED),
        )

        override fun cancel(taskId: String) = Unit
        override fun runtimeInfo(): ExtractorRuntimeInfo = ExtractorRuntimeInfo(
            component = "yt-dlp",
            installedVersion = "2026.11.18",
            requiredRange = ">=2026.1.1",
            runtimeCompatible = !incompatible,
            channel = "stable",
            verificationStatus = com.pulldock.domain.extractor.model.RuntimeVerificationStatus.VERIFIED,
            knownLimitations = emptyList(),
            checkedAt = Instant.EPOCH,
            inventoryId = "inv-1",
        )

        override fun providerHealth(providerId: ProviderId) = ProviderHealth(providerId, com.pulldock.domain.extractor.model.ProviderHealthState.CURRENT, "2026.11.18", Instant.EPOCH, null)
        override suspend fun cancelAndAwait(taskId: String) = Unit
        override suspend fun close() = Unit
    }

    private class FakeMediaPreferences : com.pulldock.domain.download.repository.MediaPreferencesRepository {
        var defaultHeight: Int? = null
        override fun observeDefaultQuality() = MutableStateFlow(defaultHeight)
        override suspend fun currentDefaultQuality(): Int? = defaultHeight
        override suspend fun setDefaultQuality(height: Int?) {
            defaultHeight = height
        }
    }

    @Test
    fun `valid magnet creates a queued torrent task`() = runTest(mainRule.testDispatcher) {
        val repo = FakeRepository()
        val coordinator = RecordingCoordinator()
        val vm = AddDownloadViewModel(
            AddTorrent(repo, coordinator, FakeLibraryForDedupe()),
            ResolveMediaUrl(FakeExtractor(incompatible = false)),
            FetchAvailableFormats(FakeExtractor(incompatible = false)),
            EnqueueDownload(repo, coordinator),
            ResolvePlaylist(FakeExtractor(incompatible = false)),
            EnqueuePlaylist(repo, FakeExtractor(incompatible = false), coordinator, FakeMediaPreferences()),
            FakeMediaPreferences(),
            androidx.lifecycle.SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onInputChanged("magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.tasks.size)
        assertEquals(DownloadType.TORRENT, repo.tasks.first().type)
        assertEquals(1, coordinator.enqueued.size)
        assertNull(vm.uiState.value.inputError)
    }

    @Test
    fun `malformed magnet surfaces a typed inline error without creating a task`() = runTest(mainRule.testDispatcher) {
        val repo = FakeRepository()
        val coordinator = RecordingCoordinator()
        val vm = AddDownloadViewModel(
            AddTorrent(repo, coordinator, FakeLibraryForDedupe()),
            ResolveMediaUrl(FakeExtractor(incompatible = false)),
            FetchAvailableFormats(FakeExtractor(incompatible = false)),
            EnqueueDownload(repo, coordinator),
            ResolvePlaylist(FakeExtractor(incompatible = false)),
            EnqueuePlaylist(repo, FakeExtractor(incompatible = false), coordinator, FakeMediaPreferences()),
            FakeMediaPreferences(),
            androidx.lifecycle.SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onInputChanged("magnet:?dn=no-hash")
        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.inputError is DownloadError.Input)
        assertTrue(repo.tasks.isEmpty())
    }

    @Test
    fun `media url resolves to formats, selection is remembered, and enqueue persists a task`() = runTest(mainRule.testDispatcher) {
        val repo = FakeRepository()
        val coordinator = RecordingCoordinator()
        val vm = AddDownloadViewModel(
            AddTorrent(repo, coordinator, FakeLibraryForDedupe()),
            ResolveMediaUrl(FakeExtractor(incompatible = false)),
            FetchAvailableFormats(FakeExtractor(incompatible = false)),
            EnqueueDownload(repo, coordinator),
            ResolvePlaylist(FakeExtractor(incompatible = false)),
            EnqueuePlaylist(repo, FakeExtractor(incompatible = false), coordinator, FakeMediaPreferences()),
            FakeMediaPreferences(),
            androidx.lifecycle.SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onInputChanged("https://www.youtube.com/watch?v=fixture")
        vm.submit()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.formats.size)
        assertNotNull(vm.uiState.value.resolvedDescriptor)

        val chosen = vm.uiState.value.formats.first()
        vm.selectFormat(chosen)
        assertEquals(chosen.id, vm.uiState.value.selectedFormat?.id)

        vm.enqueueMedia()
        advanceUntilIdle()

        assertEquals(1, repo.tasks.size)
        assertEquals(DownloadType.EXTRACTOR, repo.tasks.first().type)
        assertEquals(1, coordinator.enqueued.size)
    }

    @Test
    fun `incompatible runtime surfaces a typed extractor error, not a crash (FR-054)`() = runTest(mainRule.testDispatcher) {
        val repo = FakeRepository()
        val coordinator = RecordingCoordinator()
        val vm = AddDownloadViewModel(
            AddTorrent(repo, coordinator, FakeLibraryForDedupe()),
            ResolveMediaUrl(FakeExtractor(incompatible = true)),
            FetchAvailableFormats(FakeExtractor(incompatible = true)),
            EnqueueDownload(repo, coordinator),
            ResolvePlaylist(FakeExtractor(incompatible = false)),
            EnqueuePlaylist(repo, FakeExtractor(incompatible = false), coordinator, FakeMediaPreferences()),
            FakeMediaPreferences(),
            androidx.lifecycle.SavedStateHandle(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onInputChanged("https://example.com/video")
        vm.submit()
        advanceUntilIdle()

        val failure = vm.uiState.value.failedError
        assertTrue(failure is DownloadError.ExtractorEngine)
        assertEquals(
            DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE,
            (failure as DownloadError.ExtractorEngine).reason,
        )
        assertTrue(repo.tasks.isEmpty())
    }

    @Test
    fun `magnet opened from another app pre-fills the add field`() = runTest(mainRule.testDispatcher) {
        val repo = FakeRepository()
        val coordinator = RecordingCoordinator()
        val handle = androidx.lifecycle.SavedStateHandle(
            mapOf("source" to "magnet:?xt=urn:btih:534d07fcd3ffa994e2bf22182ac77cd51621cbd0"),
        )
        val vm = AddDownloadViewModel(
            AddTorrent(repo, coordinator, FakeLibraryForDedupe()),
            ResolveMediaUrl(FakeExtractor(incompatible = false)),
            FetchAvailableFormats(FakeExtractor(incompatible = false)),
            EnqueueDownload(repo, coordinator),
            ResolvePlaylist(FakeExtractor(incompatible = false)),
            EnqueuePlaylist(repo, FakeExtractor(incompatible = false), coordinator, FakeMediaPreferences()),
            FakeMediaPreferences(),
            handle,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("magnet:?xt=urn:btih:534d07fcd3ffa994e2bf22182ac77cd51621cbd0", vm.uiState.value.input)
        assertEquals(SourceKind.MAGNET, vm.uiState.value.sourceKind)
    }
}
