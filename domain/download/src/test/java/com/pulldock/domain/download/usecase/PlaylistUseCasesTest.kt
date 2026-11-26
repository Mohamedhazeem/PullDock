package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.ProviderId
import com.pulldock.core.model.StorageKind
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.model.DownloadGroup
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.queue.QueueState
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.LibraryQuery
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.repository.ReconcileReport
import com.pulldock.domain.extractor.ExtractorEvent
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.PlaylistDescriptor
import com.pulldock.domain.extractor.model.PlaylistEntry
import com.pulldock.domain.extractor.model.ProviderHealth
import com.pulldock.domain.extractor.model.ProviderHealthState
import com.pulldock.domain.extractor.model.RuntimeVerificationStatus
import com.pulldock.domain.torrent.model.TorrentSource
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Playlist enqueue: one task per selected entry, grouped, with per-entry failure isolation. */
class PlaylistUseCasesTest {

    private val destination = StorageDestination(StorageKind.APP_SPECIFIC, null, "App", null)

    private inner class FakeRepo : DownloadRepository {
        val tasks = mutableListOf<DownloadTask>()
        val groups = mutableListOf<DownloadGroup>()
        override fun observeTask(id: String) = MutableStateFlow<DownloadTask?>(null)
        override fun observeQueue(filter: QueueFilter) = MutableStateFlow(tasks.toList())
        override fun observeTorrentDetails(taskId: String) = MutableStateFlow<TorrentTaskDetails?>(null)
        override fun observeExtractorDetails(taskId: String) =
            MutableStateFlow<com.pulldock.domain.extractor.model.ExtractorTaskDetails?>(null)
        override suspend fun getTask(id: String) = tasks.firstOrNull { it.id == id }
        override suspend fun getTorrentDetails(taskId: String) = null
        override suspend fun getExtractorDetails(taskId: String) = null
        override suspend fun addTorrent(source: TorrentSource, destination: StorageDestination, groupId: String?) =
            error("unused")
        override suspend fun addMedia(
            descriptor: MediaDescriptor,
            format: MediaFormat,
            destination: StorageDestination,
            groupId: String?,
        ): DownloadTask {
            val task = task(descriptor.sourceUrl, groupId)
            tasks.add(task)
            return task
        }
        override suspend fun createGroup(type: DownloadType, title: String, source: String?): DownloadGroup {
            val group = DownloadGroup("g${groups.size + 1}", type, title, source, null, Instant.EPOCH)
            groups.add(group)
            return group
        }
        override fun observeGroups() = MutableStateFlow(groups.toList())
        override suspend fun getGroup(id: String) = groups.firstOrNull { it.id == id }
        override suspend fun getTasksByGroup(groupId: String) = tasks.filter { it.groupId == groupId }
        override suspend fun deleteGroup(groupId: String, deleteFiles: Boolean) {
            groups.removeAll { it.id == groupId }
        }
        override suspend fun updateState(id: String, to: DownloadState, error: DownloadError?) = true
        override suspend fun updateProgress(id: String, progress: DownloadProgress) {}
        override suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?) {}
        override suspend fun reorder(orderedIds: List<String>) {}
        override suspend fun removeTask(id: String, deleteFile: Boolean) {}
        override suspend fun findBySourceHash(hash: String) = null
        override suspend fun updateEngineTaskId(id: String, engineTaskId: String?) {}
        override suspend fun updateLimits(id: String, limits: TransferLimits) {}

        private fun task(source: String, groupId: String?): DownloadTask = DownloadTask(
            id = "t${tasks.size + 1}",
            type = DownloadType.EXTRACTOR,
            source = source,
            sourceHash = source,
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
            destination = destination,
            limits = TransferLimits.NONE,
            priority = 0,
            queueOrder = 0,
            engineTaskId = null,
            groupId = groupId,
        )
    }

    private class FakeCoordinator : QueueCoordinator {
        override val state = MutableStateFlow(QueueState(emptyList(), 0, 0, 3, emptyMap()))
        val enqueued = mutableListOf<String>()
        override suspend fun enqueue(task: DownloadTask) { enqueued.add(task.id) }
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

    private class FakeExtractor(private val failing: Set<String> = emptySet()) : MediaExtractor {
        override suspend fun resolve(url: String): ExtractorResult<MediaDescriptor> =
            if (url in failing) {
                ExtractorResult.Failure(DownloadError.Input(DownloadError.InputReason.INVALID_URL))
            } else {
                ExtractorResult.Success(
                    MediaDescriptor(url, ProviderId.YOUTUBE, "Video", null, null, "authorized"),
                )
            }

        override suspend fun availableFormats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>> =
            ExtractorResult.Success(listOf(MediaFormat("f1", "720p", "mp4", "avc1", "mp4a", 1_000L, false, null)))

        override suspend fun download(descriptor: MediaDescriptor, format: MediaFormat) = ExtractorResult.Success("et")
        override fun observeEvents(taskId: String?) = MutableStateFlow<ExtractorEvent>(ExtractorEvent.StateChanged("et", DownloadState.QUEUED))
        override fun cancel(taskId: String) = Unit
        override fun runtimeInfo() = ExtractorRuntimeInfo(
            component = "yt-dlp",
            installedVersion = "1",
            requiredRange = "1",
            runtimeCompatible = true,
            channel = "stable",
            verificationStatus = RuntimeVerificationStatus.VERIFIED,
            knownLimitations = emptyList(),
            checkedAt = Instant.EPOCH,
            inventoryId = "inv",
        )
        override fun providerHealth(providerId: ProviderId) =
            ProviderHealth(providerId, ProviderHealthState.CURRENT, "1", Instant.EPOCH, null)
        override suspend fun cancelAndAwait(taskId: String) = Unit
        override suspend fun close() = Unit
    }

    private class FakePrefs : com.pulldock.domain.download.repository.MediaPreferencesRepository {
        override fun observeDefaultQuality() = MutableStateFlow<Int?>(null)
        override suspend fun currentDefaultQuality() = null
        override suspend fun setDefaultQuality(height: Int?) = Unit
    }

    private fun entry(index: Int) = PlaylistEntry(index, "Video $index", "https://youtu.be/v$index", 60L, null)

    @Test
    fun `enqueues one grouped task per selected entry`() = runTest {
        val repo = FakeRepo()
        val coordinator = FakeCoordinator()
        val playlist = PlaylistDescriptor("https://youtube.com/playlist", ProviderId.YOUTUBE, "My Playlist", listOf(entry(0), entry(1)))
        val result = EnqueuePlaylist(repo, FakeExtractor(), coordinator, FakePrefs())(playlist, playlist.entries, destination, concurrency = 1)

        assertTrue(result is UseCaseResult.Success)
        assertEquals(2, repo.tasks.size)
        assertEquals(2, coordinator.enqueued.size)
        assertEquals(1, repo.groups.size)
        assertTrue(repo.tasks.all { it.groupId == repo.groups.first().id })
    }

    @Test
    fun `per-entry resolve failure is isolated`() = runTest {
        val repo = FakeRepo()
        val coordinator = FakeCoordinator()
        val playlist = PlaylistDescriptor("https://youtube.com/playlist", ProviderId.YOUTUBE, "Mixed", listOf(entry(0), entry(1)))
        val result = EnqueuePlaylist(repo, FakeExtractor(failing = setOf("https://youtu.be/v1")), coordinator, FakePrefs())(
            playlist,
            playlist.entries,
            destination,
            concurrency = 1,
        )

        assertTrue(result is UseCaseResult.Success)
        assertEquals(1, repo.tasks.size)
        assertEquals(1, coordinator.enqueued.size)
    }

    @Test
    fun `empty selection fails and leaves no group`() = runTest {
        val repo = FakeRepo()
        val playlist = PlaylistDescriptor("https://youtube.com/playlist", ProviderId.YOUTUBE, "Empty", emptyList())
        val result = EnqueuePlaylist(repo, FakeExtractor(), FakeCoordinator(), FakePrefs())(playlist, emptyList(), destination, concurrency = 1)

        assertTrue(result is UseCaseResult.Failure)
        assertTrue(repo.groups.isEmpty())
    }
}