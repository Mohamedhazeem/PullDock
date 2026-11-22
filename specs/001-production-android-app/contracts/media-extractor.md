# Contract: MediaExtractor and Provider

**Module**: `:domain:extractor` (interface) · `:data:extractor` (implementation)
**Source**: [plan.md](../plan.md) §Extractor Architecture

```kotlin
interface MediaExtractor {

    /** Recognize and resolve a URL into a media descriptor, or fail with a typed error. */
    suspend fun resolve(url: String): ExtractorResult<MediaDescriptor>

    /** Available formats for a resolved descriptor. May be empty (typed "no formats"). */
    suspend fun availableFormats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>>

    /** Start a download for a chosen format. */
    suspend fun download(descriptor: MediaDescriptor, format: MediaFormat): ExtractorResult<ExtractorTaskId>

    /** Progress/state stream for a download. */
    fun observeEvents(taskId: ExtractorTaskId?): Flow<ExtractorEvent>

    /** Cooperative cancellation. */
    fun cancel(taskId: ExtractorTaskId)

    /** Current runtime inventory and validation state. */
    fun runtimeInfo(): ExtractorRuntimeInfo

    /** Per-provider health. */
    fun providerHealth(providerId: ProviderId): ProviderHealth

    suspend fun close()
}

interface Provider {
    val id: ProviderId
    fun recognizes(url: String): Boolean
    suspend fun resolve(url: String): ExtractorResult<MediaDescriptor>
    suspend fun formats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>>
    fun mapError(throwable: Throwable): DownloadError
}
```

## Types

```kotlin
sealed interface ExtractorResult<out T> {
    data class Success<T>(val value: T) : ExtractorResult<T>
    data class Failure(val error: DownloadError) : ExtractorResult<T>
}

sealed interface ExtractorEvent {
    data class Progress(val taskId: ExtractorTaskId, val downloadedBytes: Long, val totalBytes: Long?, val speed: Long) : ExtractorEvent
    data class Processing(val taskId: ExtractorTaskId, val status: ProcessingStatus) : ExtractorEvent
    data class StateChanged(val taskId: ExtractorTaskId, val state: DownloadState) : ExtractorEvent
    data class Error(val taskId: ExtractorTaskId, val error: DownloadError) : ExtractorEvent
}

data class MediaDescriptor(
    val sourceUrl: String,        // sanitized
    val providerId: ProviderId,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val authorizedContext: String?,
)

data class MediaFormat(
    val id: String,
    val resolution: String?,
    val container: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val estimatedBytes: Long?,
    val mergeRequired: Boolean,
    val processingEstimateSeconds: Long?,
)

data class ProviderHealth(
    val providerId: ProviderId,
    val state: ProviderHealthState,
    val installedExtractorVersion: String?,
    val lastVerifiedAt: Instant?,
    val messageCode: String?,     // maps to user message; never raw output
)

enum class ProviderHealthState {
    CURRENT, UPDATE_RECOMMENDED, UPDATE_REQUIRED, PLATFORM_CHANGE_SUSPECTED,
    PROVIDER_TEMPORARILY_UNAVAILABLE, PROVIDER_UNSUPPORTED, AUTHORIZATION_REQUIRED,
    VERSION_CHECK_UNAVAILABLE, RUNTIME_INCOMPATIBLE
}
```

## Provider registry rules

- Providers are keyed by `ProviderId`; each is independent.
- Unknown URL → `DownloadError.InputError(UNSUPPORTED_PROVIDER)`.
- A provider failure affects only that provider's health; others remain usable.
- Provider errors map to typed `DownloadError`; raw provider output is never surfaced.
- FFmpeg/FFprobe run with argument arrays, bounded output, timeouts, and cancellation.
- Runtime/ABI: in-process pinned runtime; unsupported ABI → `RUNTIME_INCOMPATIBLE`.
