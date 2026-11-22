package com.pulldock.core.model

/** The engine kind that executes a download task. */
enum class DownloadType {
    /** BitTorrent transfer managed by the torrent engine. */
    TORRENT,

    /** Authorized media extraction managed by the extractor engine. */
    EXTRACTOR,
}
