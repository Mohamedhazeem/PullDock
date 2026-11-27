package com.pulldock.domain.download.usecase

import com.pulldock.core.model.AudioFormat
import com.pulldock.core.model.DownloadError
import com.pulldock.domain.extractor.AudioExtractor
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.download.repository.MediaLibraryRepository
import com.pulldock.domain.download.repository.MediaStorageDirectory
import java.io.File
import javax.inject.Inject

/**
 * Extracts an audio-only file from a completed library video (torrent/direct/social) and
 * publishes it as a grouped audio item (FR-016 extension). Runs the native extractor off the
 * main thread; the caller is responsible for background execution.
 */
class ExtractAudio @Inject constructor(
    private val audioExtractor: AudioExtractor,
    private val library: MediaLibraryRepository,
    private val storage: MediaStorageDirectory,
) {
    suspend operator fun invoke(itemId: String, target: AudioFormat): ExtractorResult<String> {
        val item = library.get(itemId)
            ?: return ExtractorResult.Failure(
                DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "not found"),
            )
        if (!item.fileAvailable) {
            return ExtractorResult.Failure(
                DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "file unavailable"),
            )
        }
        val dir = storage.downloadsDir()
            ?: return ExtractorResult.Failure(
                DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "no storage"),
            )
        val base = item.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "audio" }
        val out = File(dir, "$base (audio).${target.extension}")
        // fileUri is a `file:` URI (File.toURI()); FFmpeg needs a plain filesystem path.
        val sourcePath = runCatching { java.net.URI(item.fileUri).path }.getOrNull()
            ?: item.fileUri.removePrefix("file:")
        return when (val result = audioExtractor.extract(sourcePath, target, out.absolutePath)) {
            is ExtractorResult.Success -> {
                library.addAudioItem(
                    fileUri = out.absolutePath,
                    title = "${item.title} (audio)",
                    sizeBytes = out.length(),
                    groupId = item.groupId,
                    sourceHash = item.sourceHash,
                    source = item.source,
                )
                result
            }
            is ExtractorResult.Failure -> result
        }
    }
}