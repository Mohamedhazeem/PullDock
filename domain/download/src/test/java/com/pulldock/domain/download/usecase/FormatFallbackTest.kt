package com.pulldock.domain.download.usecase

import com.pulldock.domain.extractor.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resolution fallback behavior (FR-016/US3). */
class FormatFallbackTest {

    private fun format(id: String, resolution: String?, bytes: Long? = null) = MediaFormat(
        id = id,
        resolution = resolution,
        container = "mp4",
        videoCodec = "h264",
        audioCodec = "aac",
        estimatedBytes = bytes,
        mergeRequired = false,
        processingEstimateSeconds = null,
    )

    @Test
    fun `no preference leaves the choice to the user`() {
        assertNull(FormatFallback.select(listOf(format("1", "1080p")), null))
    }

    @Test
    fun `exact height wins`() {
        val formats = listOf(format("360", "360p"), format("720a", "720p", 100), format("720b", "720p", 200))
        assertEquals("720a", FormatFallback.select(formats, 720)?.id)
    }

    @Test
    fun `falls back to highest available below the preference`() {
        val formats = listOf(format("360", "360p"), format("480", "480p"), format("1080", "1080p"))
        assertEquals("480", FormatFallback.select(formats, 720)?.id)
    }

    @Test
    fun `falls back to lowest available above the preference`() {
        val formats = listOf(format("1080", "1080p"), format("1440", "1440p"))
        assertEquals("1080", FormatFallback.select(formats, 720)?.id)
    }

    @Test
    fun `audio-only formats are handled without inventing a height`() {
        val formats = listOf(format("audio", null), format("video", "1080p"))
        assertEquals("video", FormatFallback.select(formats, 720)?.id)
        assertEquals("audio", FormatFallback.select(listOf(format("audio", null)), 720)?.id)
    }

    @Test
    fun `height parsing accepts p60 suffixes`() {
        assertEquals(1080, FormatFallback.heightOf(format("x", "1080p60")))
        assertNull(FormatFallback.heightOf(format("x", "audio only")))
    }
}
