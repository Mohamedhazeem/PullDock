package com.pulldock.data.download

import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.StorageKind
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.torrent.model.TorrentSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the source-kind discriminator used by the coordinator's dispatch path. */
class TorrentSourceResolverTest {

    private fun task(source: String) = DownloadTask(
        id = "task",
        type = DownloadType.TORRENT,
        source = source,
        sourceHash = "hash",
        displayName = "Task",
        outputFilename = "",
        state = DownloadState.CREATED,
        progress = DownloadProgress.IDLE,
        totalBytes = null,
        downloadedBytes = 0,
        downloadSpeed = 0,
        uploadSpeed = null,
        etaSeconds = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        completedAt = null,
        outputUri = null,
        errorCode = null,
        errorDetails = null,
        retryCount = 0,
        nextRetryAt = null,
        destination = StorageDestination(StorageKind.APP_SPECIFIC, null, "App", null),
        limits = TransferLimits.NONE,
        priority = 0,
        queueOrder = 0,
        engineTaskId = null,
    )

    @Test
    fun `magnet sources resolve to MAGNET kind`() {
        val source = task("magnet:?xt=urn:btih:534d07fcd3ffa994e2bf22182ac77cd51621cbd0").asTorrentSource()
        assertEquals(TorrentSource.Kind.MAGNET, source.kind)
        assertEquals("magnet:?xt=urn:btih:534d07fcd3ffa994e2bf22182ac77cd51621cbd0", source.value)
    }

    @Test
    fun `SAF torrent URIs resolve to TORRENT_URI kind`() {
        val uri = "content://com.android.providers.downloads.documents/document/42"
        val source = task(uri).asTorrentSource()
        assertEquals(TorrentSource.Kind.TORRENT_URI, source.kind)
        assertEquals(uri, source.value)
    }
}
