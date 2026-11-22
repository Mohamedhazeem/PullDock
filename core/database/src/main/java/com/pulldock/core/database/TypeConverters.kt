package com.pulldock.core.database

import androidx.room.TypeConverter

/**
 * Room type converters. Tracker lists are stored newline-joined with each URL
 * percent-encoded first (java.net.URLEncoder): a tracker URI can never carry a raw
 * newline after encoding, so the join is lossless without adding a serialization
 * dependency. Timestamps are epoch milliseconds (UTC).
 */
class PullDockTypeConverters {

    @TypeConverter
    fun trackersToString(trackers: List<String>): String =
        trackers.joinToString(SEPARATOR) { java.net.URLEncoder.encode(it, Charsets.UTF_8) }

    @TypeConverter
    fun stringToTrackers(value: String): List<String> =
        value.split(SEPARATOR)
            .filter { it.isNotBlank() }
            .map { java.net.URLDecoder.decode(it, Charsets.UTF_8) }

    private companion object {
        const val SEPARATOR = "\n"
    }
}
