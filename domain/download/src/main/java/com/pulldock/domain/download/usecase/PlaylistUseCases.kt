package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.model.DownloadGroup
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.MediaPreferencesRepository
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.domain.extractor.model.PlaylistDescriptor
import com.pulldock.domain.extractor.model.PlaylistEntry
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Resolves a media URL as a playlist (FR-014). Returns the selectable entry list, or a
 * typed failure (`NOT_PLAYLIST` for a single video) so the caller can fall back to the
 * single-video flow.
 */
class ResolvePlaylist @Inject constructor(
    private val extractor: MediaExtractor,
) {
    suspend operator fun invoke(url: String): ExtractorResult<PlaylistDescriptor> {
        UrlValidator.validate(url)?.let { return ExtractorResult.Failure(it) }
        return extractor.resolvePlaylist(url.trim())
    }
}

/**
 * Creates a group and enqueues one task per selected playlist entry (US3). Each entry is
 * resolved to a descriptor + recommended format; per-entry failures are isolated so the
 * remaining entries still enqueue (FR-062). Resolution runs with bounded
 * [concurrency] (default 3) and reports progress via [onProgress]. When no entry enqueues,
 * the empty group is removed and a typed failure is returned.
 */
class EnqueuePlaylist @Inject constructor(
    private val repository: DownloadRepository,
    private val extractor: MediaExtractor,
    private val coordinator: QueueCoordinator,
    private val mediaPreferences: MediaPreferencesRepository,
) {
    suspend operator fun invoke(
        playlist: PlaylistDescriptor,
        entries: List<PlaylistEntry>,
        destination: StorageDestination,
        concurrency: Int = DEFAULT_CONCURRENCY,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
    ): UseCaseResult<DownloadGroup> {
        if (entries.isEmpty()) {
            return UseCaseResult.Failure(
                DownloadError.Input(DownloadError.InputReason.INVALID_URL, "empty playlist selection"),
            )
        }
        val group = repository.createGroup(
            type = DownloadType.EXTRACTOR,
            title = playlist.title,
            source = playlist.sourceUrl,
        )
        val quality = mediaPreferences.currentDefaultQuality()
        val total = entries.size
        val completed = AtomicInteger(0)
        val enqueued = AtomicInteger(0)
        val gate = Semaphore(concurrency.coerceAtLeast(1))

        coroutineScope {
            entries.map { entry ->
                async {
                    gate.withPermit {
                        val descriptor = when (val resolved = extractor.resolve(entry.url)) {
                            is ExtractorResult.Success -> resolved.value
                            is ExtractorResult.Failure -> null
                        }
                        val format = descriptor?.let { d ->
                            when (val available = extractor.availableFormats(d)) {
                                is ExtractorResult.Success ->
                                    FormatFallback.select(available.value, quality) ?: available.value.firstOrNull()
                                is ExtractorResult.Failure -> null
                            }
                        }
                        if (descriptor != null && format != null) {
                            val task = repository.addMedia(descriptor, format, destination, group.id)
                            coordinator.enqueue(task)
                            enqueued.incrementAndGet()
                        }
                    }
                    onProgress?.invoke(completed.incrementAndGet(), total)
                }
            }.awaitAll()
        }

        return if (enqueued.get() > 0) {
            UseCaseResult.Success(group)
        } else {
            repository.deleteGroup(group.id, deleteFiles = false)
            UseCaseResult.Failure(
                DownloadError.ExtractorEngine(
                    DownloadError.ExtractorReason.UNSUPPORTED,
                    "no playlist entries could be enqueued",
                ),
            )
        }
    }

    private companion object {
        const val DEFAULT_CONCURRENCY = 3
    }
}