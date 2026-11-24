package com.pulldock.domain.download.usecase

import com.pulldock.domain.extractor.model.MediaFormat

/**
 * Resolution fallback (FR-016/US3): with a user default (e.g. 720p), auto-select the
 * closest actually available format — exact height, else the highest below it, else
 * the lowest above it. Anything selected here is a *recommendation*: the user can still
 * change it in the format sheet. Returns null when no preference is set so the sheet
 * keeps asking every time (FR-070: no silent invented quality).
 */
object FormatFallback {

    fun select(formats: List<MediaFormat>, targetHeight: Int?): MediaFormat? {
        if (formats.isEmpty() || targetHeight == null) return null
        val withHeight = formats.mapNotNull { format ->
            heightOf(format)?.let { height -> height to format }
        }
        if (withHeight.isEmpty()) return formats.first()

        withHeight
            .filter { it.first == targetHeight }
            .minByOrNull { sizeOf(it.second) }
            ?.let { return it.second }

        withHeight
            .filter { it.first < targetHeight }
            .maxByOrNull { it.first }
            ?.let { return it.second }

        return withHeight.minByOrNull { it.first }?.second ?: formats.first()
    }

    /** Parses "720p" / "1080p60" / "480" into a height, or null for audio-only formats. */
    fun heightOf(format: MediaFormat): Int? =
        Regex("""(\d{3,4})\s*p""", RegexOption.IGNORE_CASE)
            .find(format.resolution.orEmpty())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private fun sizeOf(format: MediaFormat): Long = format.estimatedBytes ?: Long.MAX_VALUE
}
