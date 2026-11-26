package com.pulldock.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A logical group of downloads (a YouTube playlist or a multi-file torrent). Children
 * reference this row through `download_task.groupId` and `library_item.groupId`; the
 * aggregate (progress, size, state) is derived from children rather than stored here.
 */
@Entity(tableName = "download_group")
data class DownloadGroupEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "source") val source: String?,
    @ColumnInfo(name = "thumbnailUri") val thumbnailUri: String?,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
)