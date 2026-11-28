package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Internet Archive Advanced Search response (official API). */
@Serializable
data class ArchiveSearchResponse(
    val response: ArchiveResponseBody? = null,
)

@Serializable
data class ArchiveResponseBody(
    val numFound: Int? = null,
    val docs: List<ArchiveDocDto> = emptyList(),
)

@Serializable
data class ArchiveDocDto(
    val identifier: String? = null,
    val title: JsonElement? = null,
    val creator: JsonElement? = null,
    val description: JsonElement? = null,
    val date: String? = null,
    val mediatype: String? = null,
    val subject: JsonElement? = null,
    val licenseurl: String? = null,
    val publicdate: String? = null,
)

/** Internet Archive item metadata (official API). */
@Serializable
data class ArchiveMetadataResponse(
    val files: List<ArchiveFileDto> = emptyList(),
    val metadata: ArchiveMetaDto? = null,
)

@Serializable
data class ArchiveFileDto(
    val name: String? = null,
    val format: String? = null,
    val size: Long? = null,
)

@Serializable
data class ArchiveMetaDto(
    val licenseurl: String? = null,
    val title: JsonElement? = null,
    val creator: JsonElement? = null,
    val description: JsonElement? = null,
    val date: String? = null,
)

/** Archive fields may be a string or an array of strings; reduce to the first text value. */
fun JsonElement?.asText(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonArray -> firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
    else -> null
}