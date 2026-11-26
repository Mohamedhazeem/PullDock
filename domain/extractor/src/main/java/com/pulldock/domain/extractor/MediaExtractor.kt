package com.pulldock.domain.extractor

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.MediaSource
import com.pulldock.domain.extractor.model.PlaylistDescriptor
import com.pulldock.domain.extractor.model.ProcessingStatus
import kotlinx.coroutines.flow.Flow

/** Opaque extractor-side task handle (data-model.md §1.5). */
typealias ExtractorTaskId = String

/** Typed extractor operation result (contracts/media-extractor.md). */
sealed interface ExtractorResult<out T> {
    data class Success<T>(val value: T) : ExtractorResult<T>
    data class Failure(val error: DownloadError) : ExtractorResult<Nothing>
}

/** Typed extractor events; carry no yt-dlp/FFmpeg/process types (constitution VI). */
sealed interface ExtractorEvent {
    data class Progress(
        val taskId: ExtractorTaskId,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val speedBytesPerSec: Long,
    ) : ExtractorEvent

    data class Processing(val taskId: ExtractorTaskId, val status: ProcessingStatus) : ExtractorEvent
    data class StateChanged(val taskId: ExtractorTaskId, val state: DownloadState) : ExtractorEvent
    data class Error(val taskId: ExtractorTaskId, val error: DownloadError) : ExtractorEvent
}

/**
 * One media provider (FR-014): its URL patterns, resolve capability, and typed error
 * mapping. Providers are independent — one provider's failure never affects others
 * (FR-062, SC-007). Raw provider output is never surfaced (FR-049).
 */
interface Provider {
    val id: ProviderId

    /** URL-pattern recognition; pure and cheap, called before any resolution. */
    fun recognizes(url: String): Boolean

    /** Resolves an authorized URL into a descriptor or typed failure (FR-019). */
    suspend fun resolve(url: String): ExtractorResult<MediaDescriptor>

    /**
     * Resolves a playlist URL into a selectable entry list, or a typed failure
     * (`NOT_PLAYLIST` for a single video). Default providers do not support playlists.
     */
    suspend fun resolvePlaylist(url: String): ExtractorResult<PlaylistDescriptor> =
        ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "playlist unsupported"),
        )

    /** Available formats for a resolved descriptor; may be typed-empty (NO_FORMATS). */
    suspend fun formats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>>

    /** Maps provider exceptions into the typed error hierarchy. */
    fun mapError(throwable: Throwable): DownloadError
}

/**
 * Media extractor contract (FR-014–FR-022, ADR-0002). The runtime is bundled, pinned,
 * and validated at initialization (FR-053–FR-055); failures surface as typed errors,
 * never crashes (FR-054).
 */
interface MediaExtractor {

    /** Recognizes and resolves a URL via the provider registry, or fails typed. */
    suspend fun resolve(url: String): ExtractorResult<MediaDescriptor>

    /**
     * Resolves a playlist URL into a selectable entry list, or a typed failure
     * (`NOT_PLAYLIST` for a single video). Default providers do not support playlists.
     */
    suspend fun resolvePlaylist(url: String): ExtractorResult<PlaylistDescriptor> =
        ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "playlist unsupported"),
        )

    /** Available formats for a resolved descriptor; may be typed-empty. */
    suspend fun availableFormats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>>

    /** Starts a download for the chosen format; returns the extractor task id. */
    suspend fun download(descriptor: MediaDescriptor, format: MediaFormat): ExtractorResult<ExtractorTaskId>

    /** Progress/state stream; pass null for all tasks. */
    fun observeEvents(taskId: ExtractorTaskId?): Flow<ExtractorEvent>

    /** Cooperative cancellation. */
    fun cancel(taskId: ExtractorTaskId)

    /** Current bundled runtime inventory and validation state (FR-053). */
    fun runtimeInfo(): ExtractorRuntimeInfo

    /** Per-provider health (US8). */
    fun providerHealth(providerId: ProviderId): com.pulldock.domain.extractor.model.ProviderHealth

    /** Cancellation + timeout + bounded-retry control for a task (FR-018). */
    suspend fun cancelAndAwait(taskId: ExtractorTaskId)

    /** Idempotent shutdown of the runtime. */
    suspend fun close()
}
