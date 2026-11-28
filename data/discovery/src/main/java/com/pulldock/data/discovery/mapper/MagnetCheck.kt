package com.pulldock.data.discovery.mapper

/**
 * Minimal magnet validation mirroring the transfer layer's `MagnetValidator` (btih hex/base32)
 * so discovery only hands the Add flow a well-formed magnet (FR-013, FR-072). Kept local to
 * `:data:discovery` to avoid coupling discovery data to `:domain:download`.
 */
object MagnetCheck {

    private val HEX = Regex("^[0-9a-fA-F]{40}$")
    private val BASE32 = Regex("^[A-Za-z2-7]{32}$")
    private const val PREFIX = "xt=urn:btih:"

    fun isValid(magnet: String?): Boolean {
        if (magnet.isNullOrBlank()) return false
        if (!magnet.startsWith("magnet:", ignoreCase = true)) return false
        if (magnet.length > 4096) return false
        val lower = magnet.lowercase()
        val idx = lower.indexOf(PREFIX)
        if (idx < 0) return false
        val start = idx + PREFIX.length
        val end = lower.indexOf('&', start).let { if (it < 0) lower.length else it }
        val hash = magnet.substring(start, end)
        return (hash.length == 40 && hash.matches(HEX)) || (hash.length == 32 && hash.matches(BASE32))
    }

    fun infoHash(magnet: String?): String? {
        if (magnet.isNullOrBlank()) return null
        val lower = magnet.lowercase()
        val idx = lower.indexOf(PREFIX)
        if (idx < 0) return null
        val start = idx + PREFIX.length
        val end = lower.indexOf('&', start).let { if (it < 0) lower.length else it }
        return magnet.substring(start, end).lowercase().takeIf { it.matches(HEX) || it.matches(BASE32) }
    }
}