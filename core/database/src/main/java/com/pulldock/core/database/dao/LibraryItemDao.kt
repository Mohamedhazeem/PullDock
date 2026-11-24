package com.pulldock.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulldock.core.database.entity.LibraryItemEntity
import kotlinx.coroutines.flow.Flow

/** Library persistence (FR-042/FR-043). Reconciliation marks files unavailable instead of deleting records. */
@Dao
interface LibraryItemDao {

    @Query("SELECT * FROM library_item ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_item WHERE id = :id")
    suspend fun get(id: String): LibraryItemEntity?

    @Query("SELECT * FROM library_item WHERE sourceHash = :hash AND fileAvailable = 1 LIMIT 1")
    suspend fun findBySourceHash(hash: String): LibraryItemEntity?

    @Query("SELECT * FROM library_item WHERE fileUri = :fileUri")
    suspend fun findByUri(fileUri: String): LibraryItemEntity?

    @Upsert
    suspend fun upsert(item: LibraryItemEntity)

    @Query("UPDATE library_item SET fileAvailable = :available WHERE id = :id")
    suspend fun setAvailable(id: String, available: Boolean)

    @Query("DELETE FROM library_item WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM library_item")
    suspend fun count(): Int
}
