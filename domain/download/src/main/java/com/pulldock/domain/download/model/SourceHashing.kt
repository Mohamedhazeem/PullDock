package com.pulldock.domain.download.model

import com.pulldock.domain.download.usecase.MagnetValidator
import com.pulldock.domain.torrent.model.TorrentSource
import java.security.MessageDigest

/**
 * Deterministic `sourceHash` computation (invariant §3.2). Domain owns the normalized
 * key (via [MagnetValidator.dedupeHash]) and the SHA-256 projection so every caller —
 * use cases doing duplicate lookups and repositories persisting the hash — derives the
 * identical value. The hash never leaks the raw source into diagnostics.
 */
object SourceHashing {

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Hash key for a torrent source (magnet dedupes on its lowercase btih). */
    fun forTorrent(source: TorrentSource): String = sha256Hex(MagnetValidator.dedupeHash(source))

    /** Hash key for a media source URL (trimmed, case-insensitive). */
    fun forMedia(url: String): String = sha256Hex("url:${url.trim().lowercase()}")
}
