package com.pulldock.data.torrent

import com.pulldock.core.model.DownloadError
import com.pulldock.domain.torrent.model.ResumeBlob
import com.pulldock.engines.torrent.EngineObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alert → typed-error mapping (T073, T072). Native engine init/transfer behavior is
 * device-verified (T075); these cover the pure mapping the adapter depends on.
 */
class ErrorMapperTest {

    @Test
    fun `engine failure text classifies into torrent reasons`() {
        assertEquals(
            DownloadError.TorrentReason.NO_PEERS,
            ErrorMapper.reasonForEngineMessage("Connection refused: no peers available"),
        )
        assertEquals(
            DownloadError.TorrentReason.NO_TRACKERS,
            DownloadError.TorrentEngine(ErrorMapper.reasonForEngineMessage("tracker failure: timeout")).reason,
        )
        assertEquals(
            DownloadError.TorrentReason.CORRUPT_PIECE,
            DownloadError.TorrentEngine(ErrorMapper.reasonForEngineMessage("piece hash failed")).reason,
        )
    }

    @Test
    fun `failed observation maps to torrent engine error with sanitized detail`() {
        val error = ErrorMapper.fromObservation(EngineObservation.Failed("abc123", "tracker error at http://tracker.example.com/announce?key=secret"))
        assertTrue(error is DownloadError.TorrentEngine)
        // URLs never leak into persisted details (FR-049).
        val detail = (error as DownloadError.TorrentEngine).detail.orEmpty()
        assertFalse(detail.contains("http"))
        assertTrue(detail.contains("<url>"))
    }

    @Test
    fun `file failure maps to storage error`() {
        val error = ErrorMapper.fromObservation(EngineObservation.FileFailed("abc123", "movie.mkv"))
        assertTrue(error is DownloadError.Storage)
    }

    @Test
    fun `metadata failure maps to metadata timeout`() {
        val error = ErrorMapper.fromObservation(EngineObservation.MetadataFailed("abc123"))
        assertTrue(error is DownloadError.TorrentEngine)
        assertEquals(DownloadError.TorrentReason.METADATA_TIMEOUT, (error as DownloadError.TorrentEngine).reason)
    }

    @Test
    fun `sanitize bounds length and redacts urls`() {
        val long = "x".repeat(500) + " http://secret.example.com/token"
        val sanitized = ErrorMapper.sanitize(long)
        assertTrue(sanitized!!.length <= 200)
        assertFalse(sanitized.contains("http"))
    }

    @Test
    fun `resume blobs validate shape only`() {
        assertTrue(ResumeData.isUsable(ResumeBlob(version = ResumeData.CURRENT_VERSION, bytes = byteArrayOf(1))))
        assertFalse(ResumeData.isUsable(null))
        assertFalse(ResumeData.isUsable(ResumeBlob(version = 99, bytes = byteArrayOf(1))))
        assertFalse(ResumeData.isUsable(ResumeBlob(version = ResumeData.CURRENT_VERSION, bytes = byteArrayOf())))
        assertFalse(ResumeData.isUsable(ResumeBlob(version = ResumeData.CURRENT_VERSION, bytes = ByteArray(9 * 1024 * 1024))))
    }
}
