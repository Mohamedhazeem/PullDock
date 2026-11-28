package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable

/** YTS official API v2 (`list_movies.json`). */
@Serializable
data class YtsResponse(
    val status: String? = null,
    val status_message: String? = null,
    val data: YtsData? = null,
)

@Serializable
data class YtsData(
    val movie_count: Int? = null,
    val limit: Int? = null,
    val page_number: Int? = null,
    val movies: List<YtsMovie> = emptyList(),
)

@Serializable
data class YtsMovie(
    val id: Long? = null,
    val title: String? = null,
    val year: Int? = null,
    val hash: String? = null,
    val size: Long? = null,
    val seeds: Int? = null,
    val peers: Int? = null,
    val date_added: String? = null,
    val cover: String? = null,
    val torrents: List<YtsTorrent> = emptyList(),
)

@Serializable
data class YtsTorrent(
    val hash: String? = null,
    val size: Long? = null,
    val seeds: Int? = null,
    val peers: Int? = null,
    val quality: String? = null,
)