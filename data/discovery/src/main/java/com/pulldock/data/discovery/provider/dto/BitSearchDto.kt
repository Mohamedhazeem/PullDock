package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable

/**
 * BitSearch DTOs matching the documented REST schema (bitsearch.eu/api):
 * search -> { success, results:[{id, infohash, title, size, category, seeders, leechers,
 * verified, createdAt}], pagination }, detail -> { success, torrent:{..., swarm, files,
 * trackers, magnetUri, createdAt} }. Unknown keys are ignored by the lenient Json config.
 */
@Serializable
data class BitSearchSearchResponse(
    val success: Boolean? = null,
    val results: List<BitSearchResultDto> = emptyList(),
)

@Serializable
data class BitSearchResultDto(
    val id: String? = null,
    val infohash: String? = null,
    val title: String? = null,
    val size: Long? = null,
    val category: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val verified: Boolean? = null,
    val createdAt: String? = null,
)

@Serializable
data class BitSearchDetailResponse(
    val success: Boolean? = null,
    val torrent: BitSearchTorrentDto? = null,
)

@Serializable
data class BitSearchTorrentDto(
    val id: String? = null,
    val infohash: String? = null,
    val title: String? = null,
    val size: Long? = null,
    val category: String? = null,
    val verified: Boolean? = null,
    val swarm: BitSearchSwarmDto? = null,
    val files: List<BitSearchFileDto> = emptyList(),
    val trackers: List<String> = emptyList(),
    val magnetUri: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class BitSearchSwarmDto(
    val seeders: Int? = null,
    val leechers: Int? = null,
)

@Serializable
data class BitSearchFileDto(
    val path: String? = null,
    val size: Long? = null,
)