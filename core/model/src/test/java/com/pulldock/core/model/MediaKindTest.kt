package com.pulldock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Content classification drives the library's play-vs-open behavior (grouped downloads). */
class MediaKindTest {

    @Test
    fun `classifies by extension case-insensitively`() {
        assertEquals(MediaKind.VIDEO, mediaKindOf("clip.MP4"))
        assertEquals(MediaKind.VIDEO, mediaKindOf("movie.mkv"))
        assertEquals(MediaKind.AUDIO, mediaKindOf("song.FLAC"))
        assertEquals(MediaKind.IMAGE, mediaKindOf("photo.heic"))
        assertEquals(MediaKind.DOCUMENT, mediaKindOf("book.epub"))
        assertEquals(MediaKind.DOCUMENT, mediaKindOf("doc.pdf"))
        assertEquals(MediaKind.OTHER, mediaKindOf("archive.xyz"))
    }

    @Test
    fun `content uri without extension is other`() {
        assertEquals(MediaKind.OTHER, mediaKindOf("content://media/external/file/123"))
    }

    @Test
    fun `only video and audio are playable`() {
        assertTrue(MediaKind.VIDEO.playable)
        assertTrue(MediaKind.AUDIO.playable)
        assertFalse(MediaKind.IMAGE.playable)
        assertFalse(MediaKind.DOCUMENT.playable)
        assertFalse(MediaKind.OTHER.playable)
    }
}