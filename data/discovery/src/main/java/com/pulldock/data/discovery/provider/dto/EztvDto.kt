package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable

/** EZTV JSON API (`/api/get-torrents`). `torrents` may be absent. */
@Serializable
data class EztvResponse(
    val torrents_count: Int? = null,
    val limit: Int? = null,
    val page: Int? = null,
    val imdb_id: String? = null,
    val torrents: List<EztvTorrent> = emptyList(),
)

@Serializable
data class EztvTorrent(
    val id: Int? = null,
    val hash: String? = null,
    val filename: String? = null,
    val torrent_url: String? = null,
    val magnet_url: String? = null,
    val title: String? = null,
    val imdb_id: String? = null,
    val season: String? = null,
    val episode: String? = null,
    val small_screenshot: String? = null,
    val large_screenshot: String? = null,
    val seeds: Int? = null,
    val peers: Int? = null,
    val date_released_unix: String? = null,
    val size_bytes: String? = null,
)