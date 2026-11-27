package com.pulldock.domain.extractor

import com.pulldock.core.model.AudioFormat

/**
 * Extracts an audio-only track from a local media file into [target] (ADR-0013). Implemented
 * with FFmpegKit in the composition root; the domain stays free of native types.
 */
interface AudioExtractor {
    /** Writes the extracted audio to [outputFile]; returns the published path on success. */
    suspend fun extract(sourceFile: String, target: AudioFormat, outputFile: String): ExtractorResult<String>
}