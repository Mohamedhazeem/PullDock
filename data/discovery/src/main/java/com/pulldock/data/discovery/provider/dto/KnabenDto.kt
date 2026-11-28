package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable

/** Knaben Database API v2 (`/v2/search`). */
@Serializable
data class KnabenResponse(
    val hits: List<KnabenHit> = emptyList(),
)

@Serializable
data class KnabenHit(
    val hash: String? = null,
    val name: String? = null,
    val magnetUrl: String? = null,
    val tracker: String? = null,
    val seeders: Int? = null,
    val peers: Int? = null,
    val bytes: Long? = null,
    val category: Int? = null,
    val added: String? = null,
    val verified: Boolean? = null,
)