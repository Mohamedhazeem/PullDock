package com.pulldock.data.extractor

import android.content.Context
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.ExtractorEvent
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.ExtractorRuntime
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.data.extractor.provider.DirectProvider
import com.pulldock.data.extractor.provider.FacebookProvider
import com.pulldock.data.extractor.provider.InstagramProvider
import com.pulldock.data.extractor.provider.TikTokProvider
import com.pulldock.data.extractor.provider.YouTubeProvider
import com.pulldock.data.extractor.provider.XProvider
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.ProcessingStatus
import com.pulldock.domain.extractor.model.ProviderHealth
import com.pulldock.domain.extractor.model.ProviderHealthState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Provider-registry implementation of [MediaExtractor] (T086, FR-014–022).
 *
 * URL recognition stays local and typed; metadata/format discovery and downloads are
 * delegated to the pinned bundled runtime behind [ExtractorRuntime]. No Python or
 * yt-dlp type escapes this boundary (constitution VI, ADR-0012).
 */
@Singleton
class MediaExtractorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: ExtractorRuntime,
) : MediaExtractor {

    private val providers = listOf(
        YouTubeProvider(), XProvider(), InstagramProvider(), FacebookProvider(), TikTokProvider(), DirectProvider(),
    )

    private val events = MutableSharedFlow<ExtractorEvent>(extraBufferCapacity = 128)

    // The first probe already returns formats. Reusing them avoids a second slow
    // yt-dlp network extraction just to populate the format sheet.
    private val formatCache = ConcurrentHashMap<String, List<MediaFormat>>()

    override suspend fun resolve(url: String): ExtractorResult<MediaDescriptor> {
        val provider = providerFor(url) ?: return ExtractorResult.Failure(
            DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_PROVIDER),
        )
        return when (val result = runtime.info(url, provider.id)) {
            is ExtractorResult.Success -> {
                val info = result.value
                formatCache[info.sourceUrl] = info.formats
                ExtractorResult.Success(
                    MediaDescriptor(
                        sourceUrl = info.sourceUrl,
                        providerId = info.providerId,
                        title = info.title,
                        thumbnailUrl = info.thumbnailUrl,
                        durationSeconds = info.durationSeconds,
                        authorizedContext = "user-provided authorized URL",
                    ),
                )
            }
            is ExtractorResult.Failure -> result
        }
    }

    override suspend fun availableFormats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>> {
        formatCache[descriptor.sourceUrl]?.let { cached ->
            return if (cached.isEmpty()) {
                ExtractorResult.Failure(DownloadError.ExtractorEngine(DownloadError.ExtractorReason.NO_FORMATS))
            } else {
                ExtractorResult.Success(cached)
            }
        }
        val provider = providers.firstOrNull { it.id == descriptor.providerId }
            ?: return ExtractorResult.Failure(DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_PROVIDER))
        return when (val result = runtime.info(descriptor.sourceUrl, provider.id)) {
            is ExtractorResult.Success -> {
                formatCache[descriptor.sourceUrl] = result.value.formats
                if (result.value.formats.isEmpty()) {
                    ExtractorResult.Failure(
                        DownloadError.ExtractorEngine(DownloadError.ExtractorReason.NO_FORMATS),
                    )
                } else {
                    ExtractorResult.Success(result.value.formats)
                }
            }
            is ExtractorResult.Failure -> result
        }
    }

    override suspend fun download(descriptor: MediaDescriptor, format: MediaFormat): ExtractorResult<String> {
        val taskId = "${descriptor.providerId.name}-${descriptor.sourceUrl.hashCode()}"
        val tempDir = File(context.cacheDir, "partial").apply { mkdirs() }
        val needsMux = format.videoCodec != null && format.audioCodec == null
        val formats = if (needsMux) {
            formatCache[descriptor.sourceUrl] ?: when (val resolved = availableFormats(descriptor)) {
                is ExtractorResult.Success -> resolved.value
                is ExtractorResult.Failure -> return resolved
            }
        } else {
            emptyList()
        }
        // MediaMuxer is most reliable with H.264/AAC in MP4. Prefer an H.264
        // video-only format at or below the selected resolution when one exists.
        val muxVideo = if (needsMux) {
            val selectedHeight = format.resolution?.removeSuffix("p")?.toIntOrNull() ?: Int.MAX_VALUE
            formats.firstOrNull { candidate ->
                candidate.videoCodec?.contains("avc1", true) == true &&
                    (candidate.resolution?.removeSuffix("p")?.toIntOrNull() ?: 0) <= selectedHeight
            } ?: formats.firstOrNull { it.videoCodec?.contains("avc1", true) == true } ?: format
        } else {
            format
        }
        val targetContainer = when {
            !needsMux -> format.container
            muxVideo.videoCodec?.startsWith("vp8", true) == true ||
                muxVideo.videoCodec?.startsWith("vp9", true) == true -> "webm"
            else -> "mp4"
        }
        val extension = if (needsMux) ".$targetContainer" else ".mp4"
        val tempPath = File(tempDir, taskId + extension).absolutePath
        events.tryEmit(ExtractorEvent.Processing(taskId, ProcessingStatus.RUNNING))

        val audioFormat = if (needsMux) {
            val preferredAudio = if (targetContainer == "webm") "opus" else "mp4a"
            formats.firstOrNull { it.videoCodec == null && it.audioCodec?.contains(preferredAudio, true) == true }
                ?: formats.firstOrNull { it.videoCodec == null && it.audioCodec != null }
        } else {
            null
        }
        if (needsMux && audioFormat == null) {
            val error = DownloadError.ExtractorEngine(
                DownloadError.ExtractorReason.UNSUPPORTED,
                "no audio stream",
            )
            events.tryEmit(ExtractorEvent.Error(taskId, error))
            return ExtractorResult.Failure(error)
        }

        val result = if (needsMux && audioFormat != null) {
            runtime.downloadMuxed(
                url = descriptor.sourceUrl,
                videoFormatId = muxVideo.id,
                audioFormatId = audioFormat.id,
                videoCodec = muxVideo.videoCodec,
                audioCodec = audioFormat.audioCodec,
                container = targetContainer,
                outputPath = tempPath,
                onProgress = { bytes, total ->
                    events.tryEmit(ExtractorEvent.Progress(taskId, bytes, total, 0L))
                },
                height = muxVideo.resolution?.removeSuffix("p")?.toIntOrNull(),
            )
        } else {
            runtime.download(
                url = descriptor.sourceUrl,
                formatId = format.id,
                outputPath = tempPath,
                onProgress = { bytes, total ->
                    events.tryEmit(ExtractorEvent.Progress(taskId, bytes, total, 0L))
                },
                height = format.resolution?.removeSuffix("p")?.toIntOrNull(),
            )
        }

        return when (result) {
            is ExtractorResult.Success -> {
                events.tryEmit(ExtractorEvent.Processing(taskId, ProcessingStatus.DONE))
                // Return the temp path as an opaque handle; finalization owns publication.
                ExtractorResult.Success(result.value)
            }
            is ExtractorResult.Failure -> {
                events.tryEmit(ExtractorEvent.Error(taskId, result.error))
                result
            }
        }
    }

    override fun observeEvents(taskId: String?): Flow<ExtractorEvent> = events
    override fun cancel(taskId: String) = Unit
    override fun runtimeInfo(): ExtractorRuntimeInfo = runtime.runtimeInfo()

    override fun providerHealth(providerId: ProviderId): ProviderHealth {
        val info = runtime.runtimeInfo()
        return ProviderHealth(
            providerId = providerId,
            state = if (info.runtimeCompatible) ProviderHealthState.CURRENT else ProviderHealthState.RUNTIME_INCOMPATIBLE,
            installedExtractorVersion = info.installedVersion,
            lastVerifiedAt = if (info.runtimeCompatible) info.checkedAt else null,
            messageCode = if (info.runtimeCompatible) null else "PROVIDER_RUNTIME_INCOMPATIBLE",
        )
    }

    override suspend fun cancelAndAwait(taskId: String) = Unit
    override suspend fun close() = Unit

    private fun providerFor(url: String) = providers.firstOrNull { it.recognizes(url) }
}
