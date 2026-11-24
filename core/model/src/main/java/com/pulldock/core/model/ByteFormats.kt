package com.pulldock.core.model

/**
 * Human-readable byte formatting shared by screens (e.g. Home, DownloadDetail).
 * Counts use the binary units B/KB/MB/GB/TB; rates append "/s".
 */
fun formatBytes(bytes: Long): String {
    val abs = bytes.coerceAtLeast(0).toDouble()
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = abs
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "%.0f %s".format(value, units[unit]) else "%.1f %s".format(value, units[unit])
}

/** Human-readable byte rate ("1.2 MB/s"). */
fun formatSpeed(bytesPerSec: Double): String = "%s/s".format(formatBytes(bytesPerSec.toLong()))
