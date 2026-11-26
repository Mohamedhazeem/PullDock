package com.pulldock.domain.extractor.model

import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.ProviderId

/**
 * A selectable output format for a resolved media source (data-model.md §1.4).
 * `estimatedBytes` is advisory metadata from the provider, never a guarantee.
 */
data class MediaFormat(
    val id: String,
    val resolution: String?,
    val container: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val estimatedBytes: Long?,
    /** Separate video/audio streams that must be merged (FR-016). */
    val mergeRequired: Boolean,
    val processingEstimateSeconds: Long?,
    val language: String? = null,
    /** Higher is the provider's original/default track; -1 marks translated audio. */
    val languagePreference: Int? = null,
)

/**
 * A resolved, authorized media source. [sourceUrl] is sanitized (no credentials) and
 * [authorizedContext] records the user's authorization basis for this download (US3,
 * FR-020: never bypassing DRM/paywalls/authentication/private accounts).
 */
data class MediaDescriptor(
    val sourceUrl: String,
    val providerId: ProviderId,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val authorizedContext: String?,
)

/** How the media source was added: pasted URL, share intent, or clipboard-free paste UI. */
enum class MediaSourceKind { PASTED_URL, SHARED_URL, TORRENT_FILE }

/**
 * User-facing media source entry combining a URL with its authorization attestation.
 * Used by the add flow before resolution; distinct from [MediaDescriptor], which is the
 * provider-verified result.
 */
data class MediaSource(
    val url: String,
    val providerId: ProviderId?,
    val kind: MediaSourceKind,
    val authorizedContext: String?,
)

/** Processing status of a media download's merge/finalize stage. */
enum class ProcessingStatus { NONE, PENDING, RUNNING, DONE, FAILED }

/**
 * One entry of a resolved playlist (e.g. a YouTube playlist video). [url] is the
 * per-entry authorized source; [index] is the provider's 0-based position.
 */
data class PlaylistEntry(
    val index: Int,
    val title: String,
    val url: String,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
)

/**
 * A resolved playlist awaiting per-entry selection (data-model.md §1.4). The user picks
 * entries; each selected entry is resolved to a descriptor+format and enqueued as a
 * grouped task.
 */
data class PlaylistDescriptor(
    val sourceUrl: String,
    val providerId: ProviderId,
    val title: String,
    val entries: List<PlaylistEntry>,
)

/** Per-task extractors detail keyed by [taskId] (data-model.md §1.3). */
data class ExtractorTaskDetails(
    val taskId: String,
    val providerId: ProviderId,
    /** Sanitized original URL (no credentials/tokens). */
    val originalUrl: String,
    val selectedFormat: MediaFormat?,
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val mergeRequired: Boolean,
    val processingStatus: ProcessingStatus,
    /** Typed provider error code; raw provider output is never stored. */
    val providerError: String?,
    /** Extractor runtime inventory id in effect at resolve time. */
    val runtimeInfoId: String?,
    /** Which engine executes this task (kept for the unified queue). */
    val downloadType: DownloadType = DownloadType.EXTRACTOR,
)
