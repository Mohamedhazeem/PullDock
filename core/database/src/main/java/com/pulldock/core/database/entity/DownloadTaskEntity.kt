package com.pulldock.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable download task row (data-model.md §4.1 `download_task`). Complex domain
 * values are flattened into columns; engine-specific data lives in the torrent/extractor
 * detail rows (invariant §3.9: no engine types on the common task).
 */
@Entity(
    tableName = "download_task",
    indices = [
        Index(value = ["sourceHash"], unique = true),
        Index(value = ["state"]),
        Index(value = ["type"]),
        Index(value = ["createdAt"]),
        Index(value = ["priority"]),
        Index(value = ["queueOrder"]),
    ],
)
data class DownloadTaskEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "sourceHash") val sourceHash: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "resolution") val resolution: String? = null,
    @ColumnInfo(name = "outputFilename") val outputFilename: String,
    @ColumnInfo(name = "state") val state: String,
    /** Monotonic per-task transition sequence; grows on every persisted transition. */
    @ColumnInfo(name = "stateSequence") val stateSequence: Long,
    @ColumnInfo(name = "totalBytes") val totalBytes: Long?,
    @ColumnInfo(name = "downloadedBytes") val downloadedBytes: Long,
    @ColumnInfo(name = "downloadSpeed") val downloadSpeed: Long,
    @ColumnInfo(name = "uploadSpeed") val uploadSpeed: Long?,
    @ColumnInfo(name = "etaSeconds") val etaSeconds: Long?,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "completedAt") val completedAt: Long?,
    @ColumnInfo(name = "outputUri") val outputUri: String?,
    @ColumnInfo(name = "errorCode") val errorCode: String?,
    @ColumnInfo(name = "errorDetails") val errorDetails: String?,
    @ColumnInfo(name = "retryCount") val retryCount: Int,
    @ColumnInfo(name = "nextRetryAt") val nextRetryAt: Long?,
    @ColumnInfo(name = "destinationKind") val destinationKind: String,
    @ColumnInfo(name = "destinationUri") val destinationUri: String?,
    @ColumnInfo(name = "destinationDisplayName") val destinationDisplayName: String,
    @ColumnInfo(name = "limitsDownloadRate") val limitsDownloadRate: Long?,
    @ColumnInfo(name = "limitsUploadRate") val limitsUploadRate: Long?,
    @ColumnInfo(name = "limitsMaxConnections") val limitsMaxConnections: Int?,
    @ColumnInfo(name = "priority") val priority: Int,
    @ColumnInfo(name = "queueOrder") val queueOrder: Int,
    @ColumnInfo(name = "engineTaskId") val engineTaskId: String?,
)
