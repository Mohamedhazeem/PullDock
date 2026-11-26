package com.pulldock.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pulldock.core.database.dao.DownloadGroupDao
import com.pulldock.core.database.dao.DownloadTaskDao
import com.pulldock.core.database.dao.ExtractorTaskDao
import com.pulldock.core.database.dao.LibraryItemDao
import com.pulldock.core.database.dao.RecoveryMarkerDao
import com.pulldock.core.database.dao.TorrentTaskDao
import com.pulldock.core.database.dao.UsageDailyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the process-wide [PullDockDatabase] and its DAOs (constitution VII). The
 * database uses WAL journaling with foreign keys enabled; migrations are wired via
 * [Migrations.ALL] and destructive fallback is never configured — a failed migration
 * surfaces as a typed persistence failure instead of deleting user data.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PullDockDatabase =
        Room.databaseBuilder(context, PullDockDatabase::class.java, PullDockDatabase.DATABASE_NAME)
            .addMigrations(*Migrations.ALL.toTypedArray())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideDownloadTaskDao(database: PullDockDatabase): DownloadTaskDao = database.downloadTaskDao()

    @Provides
    fun provideTorrentTaskDao(database: PullDockDatabase): TorrentTaskDao = database.torrentTaskDao()

    @Provides
    fun provideExtractorTaskDao(database: PullDockDatabase): ExtractorTaskDao = database.extractorTaskDao()

    @Provides
    fun provideRecoveryMarkerDao(database: PullDockDatabase): RecoveryMarkerDao = database.recoveryMarkerDao()

    @Provides
    fun provideLibraryItemDao(database: PullDockDatabase): LibraryItemDao = database.libraryItemDao()

    @Provides
    fun provideDownloadGroupDao(database: PullDockDatabase): DownloadGroupDao = database.downloadGroupDao()

    @Provides
    fun provideUsageDailyDao(database: PullDockDatabase): UsageDailyDao = database.usageDailyDao()
}
