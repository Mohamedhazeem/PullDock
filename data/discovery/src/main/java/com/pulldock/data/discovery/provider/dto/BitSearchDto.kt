package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable

/**
 * BitSearch DTOs (documented REST). Field names follow the documented search/detail shapes;
 * unknown keys are ignored by the lenient Json config. All optional fields default to null so
 * missing metadata is never fabricated (FR-014, FR-052). Verify exact fields against live docs
 * at implementation time (research D15) and encode fixtures.
 */
@Serializable
data class BitSearchSearchResponse(
    val results: List<BitSearchResultDto> = emptyList(),
)

@Serializable
data class BitSearchResultDto(
    val id: String? = null,
    val infoHash: String? = null,
    val title: String? = null,
    val size: Long? = null,
    val category: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val verified: Boolean? = null,
    val created: String? = null,
)

@Serializable
data class BitSearchDetailResponse(
    val id: String? = null,
    val infoHash: String? = null,
    val title: String? = null,
    val size: Long? = null,
    val category: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val magnetUri: String? = null,
    val files: List<String> = emptyList(),
    val trackers: List<String> = emptyList(),
    val created: String? = null,
)