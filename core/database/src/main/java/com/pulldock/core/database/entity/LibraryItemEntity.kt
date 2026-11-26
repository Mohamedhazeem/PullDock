package com.pulldock.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Completed-media library record (data-model.md §4.1/§4.6, FR-042/FR-043). The link to
 * `download_task` is logical, not FK-enforced, so file-only records remain possible.
 * [fileAvailable] is updated by reconciliation; unavailable items are never playable.
 */
@Entity(
    tableName = "library_item",
    indices = [
        Index(value = ["fileUri"], unique = true),
        Index(value = ["type"]),
        Index(value = ["completedAt"]),
    ],
)
data class LibraryItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "fileUri") val fileUri: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "sizeBytes") val sizeBytes: Long?,
    @ColumnInfo(name = "resolution") val resolution: String? = null,
    @ColumnInfo(name = "durationSeconds") val durationSeconds: Long?,
    @ColumnInfo(name = "source") val source: String?,
    @ColumnInfo(name = "completedAt") val completedAt: Long,
    @ColumnInfo(name = "thumbnailUri") val thumbnailUri: String?,
    @ColumnInfo(name = "fileAvailable") val fileAvailable: Boolean = true,
    /** Deterministic source key used to prevent re-downloading an existing file. */
    @ColumnInfo(name = "sourceHash") val sourceHash: String? = null,
)
