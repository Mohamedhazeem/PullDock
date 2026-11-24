package com.pulldock.data.download

import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.torrent.model.TorrentSource

/**
 * Resolves the engine source kind from a persisted task. The coordinator stores only
 * the source string, so the magnet prefix is the authoritative discriminator; every
 * other value is a SAF/content URI pointing at a `.torrent` document (FR-001).
 */
internal fun DownloadTask.asTorrentSource(): TorrentSource =
    if (source.startsWith("magnet:", ignoreCase = true)) {
        TorrentSource(TorrentSource.Kind.MAGNET, source)
    } else {
        TorrentSource(TorrentSource.Kind.TORRENT_URI, source)
    }
