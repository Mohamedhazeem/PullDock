package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.SourceHashing
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import javax.inject.Inject

/** URL validation shared by the media use cases (FR-020, ADR-0010 input rules). */
object UrlValidator {

    private const val MAX_URL_LENGTH = 4_096

    /** `null` when the URL is structurally valid http(s); else the typed error. */
    fun validate(url: String): DownloadError? = when {
        url.isBlank() -> DownloadError.Input(DownloadError.InputReason.INVALID_URL, "empty")
        url.length > MAX_URL_LENGTH -> DownloadError.Input(DownloadError.InputReason.INVALID_URL, "too long")
        url.contains(charWhitespace()) -> DownloadError.Input(DownloadError.InputReason.INVALID_URL, "whitespace")
        !url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true) ->
            DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_SCHEME)
        else -> null
    }

    private fun charWhitespace(): Regex = Regex("\\s")
}

/**
 * Resolves a media URL through the provider registry (FR-014/FR-019). The URL is
 * structurally validated first; unknown providers surface `UNSUPPORTED_PROVIDER`
 * without a bypass attempt (FR-020). Resolution is only claimed on a real descriptor.
 */
class ResolveMediaUrl @Inject constructor(
    private val extractor: MediaExtractor,
) {
    suspend operator fun invoke(url: String): ExtractorResult<MediaDescriptor> {
        UrlValidator.validate(url)?.let { return ExtractorResult.Failure(it) }
        return extractor.resolve(url.trim())
    }
}

/** Formats for a resolved descriptor (FR-015); typed-empty when none exist. */
class FetchAvailableFormats @Inject constructor(
    private val extractor: MediaExtractor,
) {
    suspend operator fun invoke(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>> =
        extractor.availableFormats(descriptor)
}

/**
 * Creates and enqueues a media download for the chosen format (US3 scenario 2).
 * Requires a resolved descriptor (FR-019) and duplicate-guards on the URL.
 */
class EnqueueDownload @Inject constructor(
    private val repository: DownloadRepository,
    private val coordinator: QueueCoordinator,
) {
    suspend operator fun invoke(
        descriptor: MediaDescriptor,
        format: MediaFormat,
        destination: StorageDestination,
    ): UseCaseResult<DownloadTask> {
        UrlValidator.validate(descriptor.sourceUrl)?.let { return UseCaseResult.Failure(it) }

        // Duplicate guard must use the same SHA-256 projection the repository persists
        // (SourceHashing.forMedia); passing the raw "url:…" string never matched, so a
        // re-added URL slipped through and crashed on the UNIQUE sourceHash constraint.
        val existing = repository.findBySourceHash(SourceHashing.forMedia(descriptor.sourceUrl))
        if (existing != null) {
            return UseCaseResult.Failure(DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "duplicate"))
        }

        val task = repository.addMedia(descriptor, format, destination)
        coordinator.enqueue(task)
        return UseCaseResult.Success(task)
    }
}
