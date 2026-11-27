package com.pulldock.extractor

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.pulldock.core.model.AudioFormat
import com.pulldock.core.model.DownloadError
import com.pulldock.domain.extractor.AudioExtractor
import com.pulldock.domain.extractor.ExtractorResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FFmpegKit-backed [AudioExtractor] (ADR-0013). Re-encodes the source audio to the target
 * format; runs off the main thread. The source is always re-encoded so ADTS AAC and any
 * source container are handled uniformly (no bitstream filter needed).
 */
@Singleton
class FfmpegAudioExtractor @Inject constructor() : AudioExtractor {

    override suspend fun extract(
        sourceFile: String,
        target: AudioFormat,
        outputFile: String,
    ): ExtractorResult<String> = withContext(Dispatchers.IO) {
        val src = File(sourceFile.removePrefix("file:"))
        val out = File(outputFile)
        if (!src.isFile || src.length() <= 0L) {
            return@withContext ExtractorResult.Failure(
                DownloadError.ExtractorEngine(
                    DownloadError.ExtractorReason.UNSUPPORTED,
                    "no source audio",
                ),
            )
        }
        try {
            val session = FFmpegKit.execute(
                "-y -i \"$sourceFile\" -vn ${target.ffmpegArgs} \"$outputFile\"",
            )
            if (ReturnCode.isSuccess(session.returnCode) && out.isFile && out.length() > 0L) {
                ExtractorResult.Success(outputFile)
            } else {
                ExtractorResult.Failure(
                    DownloadError.ExtractorEngine(
                        DownloadError.ExtractorReason.UNSUPPORTED,
                        "audio extraction failed",
                    ),
                )
            }
        } catch (t: Throwable) {
            ExtractorResult.Failure(
                DownloadError.ExtractorEngine(
                    DownloadError.ExtractorReason.UNSUPPORTED,
                    "audio extraction error",
                ),
            )
        }
    }
}