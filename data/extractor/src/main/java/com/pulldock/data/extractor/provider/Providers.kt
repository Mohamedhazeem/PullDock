package com.pulldock.data.extractor.provider

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.data.extractor.runtime.BundledRuntimeCatalog
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat

/**
 * Base provider implementation (FR-014/FR-062). Each provider is independent: resolve
 * attempts delegate to the bundled runtime and currently surface the typed
 * `RUNTIME_INCOMPATIBLE` state because the T080 device PoC has not yet verified the
 * bundled combination. When the verified runtime lands, each provider's resolve call
 * is implemented against it — recognition and error mapping are already real.
 *
 * Raw provider output never reaches this layer's callers (FR-049); failures map to
 * typed errors only.
 */
abstract class BaseProvider(override val id: ProviderId) : com.pulldock.domain.extractor.Provider {

    override fun recognizes(url: String): Boolean = UrlRecognizer.recognize(url) == id

    override suspend fun resolve(url: String): ExtractorResult<MediaDescriptor> = runtimeUnavailable()

    override suspend fun formats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>> = runtimeUnavailable()

    override fun mapError(throwable: Throwable): DownloadError = when (throwable) {
        is kotlinx.coroutines.CancellationException -> throw throwable
        else -> DownloadError.ExtractorEngine(
            DownloadError.ExtractorReason.PROVIDER_CHANGED,
            null, // sanitized: no raw exception text (FR-049)
        )
    }

    private fun <T> runtimeUnavailable(): ExtractorResult<T> = ExtractorResult.Failure(
        DownloadError.ExtractorEngine(
            DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE,
            "bundled runtime pending verification (T080)",
        ),
    )
}

class YouTubeProvider : BaseProvider(ProviderId.YOUTUBE)
class XProvider : BaseProvider(ProviderId.X)
class InstagramProvider : BaseProvider(ProviderId.INSTAGRAM)
class FacebookProvider : BaseProvider(ProviderId.FACEBOOK)
class TikTokProvider : BaseProvider(ProviderId.TIKTOK)
