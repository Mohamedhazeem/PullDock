package com.pulldock.data.extractor

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.data.extractor.provider.UrlRecognizer
import com.pulldock.data.extractor.provider.YouTubeProvider
import com.pulldock.data.extractor.provider.XProvider
import com.pulldock.data.extractor.provider.InstagramProvider
import com.pulldock.data.extractor.provider.FacebookProvider
import com.pulldock.data.extractor.provider.TikTokProvider
import com.pulldock.data.extractor.runtime.BundledRuntimeCatalog
import com.pulldock.domain.extractor.ExtractorEvent
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.ProviderHealth
import com.pulldock.domain.extractor.model.ProviderHealthState
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Provider-registry implementation of [MediaExtractor] (T086, contracts/media-extractor.md).
 *
 * Resolution workflow: structural URL validation → provider recognition
 * (`UNSUPPORTED_PROVIDER` for unknown hosts, never a bypass) → provider resolve.
 * Resolution is only claimed on a real descriptor (FR-019).
 *
 * The bundled runtime is pinned and currently pending device verification (T080);
 * provider resolves surface the typed `RUNTIME_INCOMPATIBLE` state (FR-054) and every
 * provider's health reflects the runtime state. Provider failures are isolated:
 * one provider's error never disables another (FR-062, SC-007).
 */
@Singleton
class MediaExtractorImpl @Inject constructor() : MediaExtractor {

    private val providers = listOf(
        YouTubeProvider(),
        XProvider(),
        InstagramProvider(),
        FacebookProvider(),
        TikTokProvider(),
    )

    private val runtimeInfo by lazy {
        BundledRuntimeCatalog.inventory(Instant.now()).first { it.component == "yt-dlp" }
    }

    private val events = MutableSharedFlow<ExtractorEvent>(extraBufferCapacity = 64)

    override suspend fun resolve(url: String): ExtractorResult<MediaDescriptor> {
        val provider = providerFor(url) ?: return ExtractorResult.Failure(
            DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_PROVIDER),
        )
        return provider.resolve(url)
    }

    override suspend fun availableFormats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>> {
        val provider = providers.firstOrNull { it.id == descriptor.providerId }
            ?: return ExtractorResult.Failure(DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_PROVIDER))
        return provider.formats(descriptor)
    }

    override suspend fun download(descriptor: MediaDescriptor, format: MediaFormat): ExtractorResult<String> {
        // The download pipeline (temp → merge → finalize, T088) lands with the verified
        // runtime; until then the typed runtime state prevents fake downloads.
        return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(
                DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE,
                "bundled runtime pending verification (T080)",
            ),
        )
    }

    override fun observeEvents(taskId: String?): Flow<ExtractorEvent> = events

    override fun cancel(taskId: String) = Unit

    override fun runtimeInfo(): ExtractorRuntimeInfo = runtimeInfo

    override fun providerHealth(providerId: ProviderId): ProviderHealth = ProviderHealth(
        providerId = providerId,
        state = if (BundledRuntimeCatalog.RUNTIME_VERIFIED) ProviderHealthState.CURRENT else ProviderHealthState.RUNTIME_INCOMPATIBLE,
        installedExtractorVersion = runtimeInfo.installedVersion,
        lastVerifiedAt = null,
        messageCode = if (BundledRuntimeCatalog.RUNTIME_VERIFIED) null else "PROVIDER_RUNTIME_INCOMPATIBLE",
    )

    override suspend fun cancelAndAwait(taskId: String) = Unit

    override suspend fun close() = Unit

    private fun providerFor(url: String) = providers.firstOrNull { it.recognizes(url) }
}
