package com.pulldock.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulldock.core.database.entity.DownloadGroupEntity
import kotlinx.coroutines.flow.Flow

/** Persistence for grouped downloads (playlists, multi-file torrents). */
@Dao
interface DownloadGroupDao {

    @Upsert
    suspend fun upsert(group: DownloadGroupEntity)

    @Query("SELECT * FROM download_group WHERE id = :id")
    suspend fun getById(id: String): DownloadGroupEntity?

    @Query("SELECT * FROM download_group ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadGroupEntity>>

    @Query("DELETE FROM download_group WHERE id = :id")
    suspend fun delete(id: String)
}