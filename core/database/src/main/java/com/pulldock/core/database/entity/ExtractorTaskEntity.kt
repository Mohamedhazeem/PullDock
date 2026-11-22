package com.pulldock.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Extractor engine detail row (data-model.md §4.1 `extractor_task`). The selected
 * format is flattened to columns; `originalUrl` is stored sanitized (no credentials,
 * data-model.md §4.7).
 */
@Entity(
    tableName = "extractor_task",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["providerId"])],
)
data class ExtractorTaskEntity(
    @PrimaryKey @ColumnInfo(name = "taskId") val taskId: String,
    @ColumnInfo(name = "providerId") val providerId: String,
    @ColumnInfo(name = "originalUrl") val originalUrl: String,
    // Selected format (flattened MediaFormat; null until the user chooses).
    @ColumnInfo(name = "formatId") val formatId: String?,
    @ColumnInfo(name = "formatResolution") val formatResolution: String?,
    @ColumnInfo(name = "formatContainer") val formatContainer: String?,
    @ColumnInfo(name = "formatVideoCodec") val formatVideoCodec: String?,
    @ColumnInfo(name = "formatAudioCodec") val formatAudioCodec: String?,
    @ColumnInfo(name = "formatEstimatedBytes") val formatEstimatedBytes: Long?,
    @ColumnInfo(name = "formatMergeRequired") val formatMergeRequired: Boolean?,
    @ColumnInfo(name = "formatProcessingEstimateSeconds") val formatProcessingEstimateSeconds: Long?,
    // Resulting media properties once processing is known.
    @ColumnInfo(name = "container") val container: String?,
    @ColumnInfo(name = "videoCodec") val videoCodec: String?,
    @ColumnInfo(name = "audioCodec") val audioCodec: String?,
    @ColumnInfo(name = "mergeRequired") val mergeRequired: Boolean,
    @ColumnInfo(name = "processingStatus") val processingStatus: String,
    @ColumnInfo(name = "providerError") val providerError: String?,
    @ColumnInfo(name = "runtimeInfoId") val runtimeInfoId: String?,
)
