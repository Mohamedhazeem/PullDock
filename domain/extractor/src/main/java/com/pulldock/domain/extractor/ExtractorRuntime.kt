package com.pulldock.domain.extractor

import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaFormat

/**
 * Typed boundary to the bundled extractor runtime (ADR-0002/ADR-0012). No Python,
 * yt-dlp, process or file-handle types cross this interface.
 */
interface ExtractorRuntime {

    /** Lightweight resolved metadata needed before a format is chosen. */
    data class MediaInfo(
        val sourceUrl: String,
        val providerId: ProviderId,
        val title: String,
        val durationSeconds: Long?,
        val thumbnailUrl: String?,
        val formats: List<MediaFormat>,
    )

    /** Extracts metadata from an authorized URL; never bypasses DRM/login walls. */
    suspend fun info(url: String, providerId: ProviderId): ExtractorResult<MediaInfo>

    /** Downloads one selected format into [outputPath], reporting progress in bytes. */
    suspend fun download(
        url: String,
        formatId: String,
        outputPath: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): ExtractorResult<String>

    fun runtimeInfo(): ExtractorRuntimeInfo
}
