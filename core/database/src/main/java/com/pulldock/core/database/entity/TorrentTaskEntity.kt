package com.pulldock.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Torrent engine detail row (data-model.md §4.1 `torrent_task`). Cascade-deletes with
 * its task. Resume data is an opaque, versioned blob the domain never parses.
 */
@Entity(
    tableName = "torrent_task",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["infohash"], unique = true)],
)
data class TorrentTaskEntity(
    @PrimaryKey @ColumnInfo(name = "taskId") val taskId: String,
    @ColumnInfo(name = "infohash") val infohash: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "totalSize") val totalSize: Long,
    /** Newline-joined, percent-encoded tracker URLs (safe for any URI content). */
    @ColumnInfo(name = "trackers") val trackers: String,
    @ColumnInfo(name = "peerCount") val peerCount: Int,
    @ColumnInfo(name = "seedCount") val seedCount: Int = 0,
    @ColumnInfo(name = "sequentialDownload") val sequentialDownload: Boolean,
    @ColumnInfo(name = "resumeData") val resumeData: ByteArray?,
    @ColumnInfo(name = "resumeDataVersion") val resumeDataVersion: Int,
    @ColumnInfo(name = "seedAfterCompletion") val seedAfterCompletion: Boolean,
    @ColumnInfo(name = "maxUploadRateBytesPerSec") val maxUploadRateBytesPerSec: Long?,
    @ColumnInfo(name = "stopRatio") val stopRatio: Double?,
    @ColumnInfo(name = "timeLimitSeconds") val timeLimitSeconds: Long?,
    @ColumnInfo(name = "perTorrentOverride") val perTorrentOverride: Boolean,
    @ColumnInfo(name = "uploadedBytes") val uploadedBytes: Long,
)

/** One file of a torrent; `taskId + fileIndex` is the natural composite key. */
@Entity(
    tableName = "torrent_file",
    primaryKeys = ["taskId", "fileIndex"],
    foreignKeys = [
        ForeignKey(
            entity = TorrentTaskEntity::class,
            parentColumns = ["taskId"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["taskId"])],
)
data class TorrentFileEntity(
    @ColumnInfo(name = "taskId") val taskId: String,
    @ColumnInfo(name = "fileIndex") val fileIndex: Int,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "sizeBytes") val sizeBytes: Long,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "selected") val selected: Boolean,
)
