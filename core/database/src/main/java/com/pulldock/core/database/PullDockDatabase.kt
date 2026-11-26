package com.pulldock.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pulldock.core.database.dao.LibraryItemDao
import com.pulldock.core.database.dao.DownloadGroupDao
import com.pulldock.core.database.dao.DownloadTaskDao
import com.pulldock.core.database.dao.ExtractorTaskDao
import com.pulldock.core.database.dao.RecoveryMarkerDao
import com.pulldock.core.database.dao.TorrentTaskDao
import com.pulldock.core.database.dao.UsageDailyDao
import com.pulldock.core.database.entity.DownloadGroupEntity
import com.pulldock.core.database.entity.DownloadTaskEntity
import com.pulldock.core.database.entity.ExtractorTaskEntity
import com.pulldock.core.database.entity.LibraryItemEntity
import com.pulldock.core.database.entity.RecoveryMarkerEntity
import com.pulldock.core.database.entity.TorrentTaskEntity
import com.pulldock.core.database.entity.TorrentFileEntity
import com.pulldock.core.database.entity.UsageDailyEntity

/**
 * PullDock's durable source of application state (constitution VII), schema v1:
 * unified task row, torrent/extractor detail rows, torrent files, and recovery
 * markers, and the completed-media library (data-model.md §4.1). Foreign keys cascade on task deletion; every future
 * schema change ships with a tested migration (`Migrations`) — destructive fallback
 * is forbidden.
 */
@Database(
    entities = [
        DownloadTaskEntity::class,
        TorrentTaskEntity::class,
        TorrentFileEntity::class,
        ExtractorTaskEntity::class,
        RecoveryMarkerEntity::class,
        LibraryItemEntity::class,
        DownloadGroupEntity::class,
        UsageDailyEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class PullDockDatabase : RoomDatabase() {

    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun torrentTaskDao(): TorrentTaskDao
    abstract fun extractorTaskDao(): ExtractorTaskDao
    abstract fun recoveryMarkerDao(): RecoveryMarkerDao
    abstract fun libraryItemDao(): LibraryItemDao
    abstract fun downloadGroupDao(): DownloadGroupDao
    abstract fun usageDailyDao(): UsageDailyDao

    companion object {
        const val DATABASE_NAME: String = "pulldock.db"
    }
}
