package com.pulldock.data.extractor.provider

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.Provider
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat

/**
 * Fallback provider for any other authorized http(s) URL (audio, image, video or plain
 * file). Recognition is local; metadata and formats come from the bundled runtime via
 * [com.pulldock.data.extractor.MediaExtractorImpl]. No auth/DRM bypass is attempted.
 */
class DirectProvider : Provider {
    override val id: ProviderId = ProviderId.DIRECT

    override fun recognizes(url: String): Boolean =
        UrlRecognizer.recognize(url) == null &&
            (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))

    override suspend fun resolve(url: String): ExtractorResult<MediaDescriptor> =
        error("Direct resolution is handled by MediaExtractorImpl")

    override suspend fun formats(descriptor: MediaDescriptor): ExtractorResult<List<MediaFormat>> =
        error("Direct formats are handled by MediaExtractorImpl")

    override fun mapError(throwable: Throwable): DownloadError = DownloadError.ExtractorEngine(
        DownloadError.ExtractorReason.UNSUPPORTED,
    )
}
