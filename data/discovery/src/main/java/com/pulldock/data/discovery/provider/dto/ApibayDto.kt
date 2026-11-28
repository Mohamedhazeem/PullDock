package com.pulldock.data.discovery.provider.dto

import kotlinx.serialization.Serializable

/**
 * The Pirate Bay via apibay JSON. `q.php`/`f.php` return a JSON array; `t.php` returns a single
 * object (modeled separately). Unofficial and untrusted; used only when the user enables TPB.
 */
@Serializable
data class ApibayTorrent(
    val id: String? = null,
    val name: String? = null,
    val info_hash: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val size: String? = null,
    val category: String? = null,
    val added: String? = null,
    val status: String? = null,
    val username: String? = null,
    val num_files: Int? = null,
)

@Serializable
data class ApibayDetail(
    val id: String? = null,
    val name: String? = null,
    val info_hash: String? = null,
    val descr: String? = null,
    val seeders: Int? = null,
    val leechers: Int? = null,
    val size: String? = null,
    val category: String? = null,
    val added: String? = null,
    val num_files: Int? = null,
)

@Serializable
data class ApibayFile(
    val name: String? = null,
    val size: String? = null,
)