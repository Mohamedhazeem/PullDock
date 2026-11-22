package com.pulldock.domain.torrent.model

/** Where a torrent transfer originates (FR-001). */
data class TorrentSource(
    val kind: Kind,
    /**
     * For [Kind.MAGNET]: the magnet URI. For [Kind.TORRENT_URI]: a content/file URI of
     * the picked `.torrent` document (SAF), readable by the adapter.
     */
    val value: String,
) {
    enum class Kind { MAGNET, TORRENT_URI }
}
