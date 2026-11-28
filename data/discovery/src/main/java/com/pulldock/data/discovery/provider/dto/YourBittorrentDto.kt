package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable

/** YourBittorrent JSON API (`/api/search.json`). */
@Serializable
data class YbtResponse(
    val status: String? = null,
    val results: List<YbtResult> = emptyList(),
)

@Serializable
data class YbtResult(
    val id: Long? = null,
    val name: String? = null,
    val infohash: String? = null,
    val magnet: String? = null,
    val torrent_file: String? = null,
    val url: String? = null,
    val category: String? = null,
    val category_id: Int? = null,
    val size_bytes: Long? = null,
    val seeds: Int? = null,
    val peers: Int? = null,
    val verified: Boolean? = null,
    val added: Long? = null,
)