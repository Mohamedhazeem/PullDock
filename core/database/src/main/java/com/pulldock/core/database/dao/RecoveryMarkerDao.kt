package com.pulldock.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulldock.core.database.entity.RecoveryMarkerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Recovery marker persistence (US9). Startup recovery reads every marker and either
 * restores engine sessions (`recoverable = 1`) or marks tasks `NEEDS_USER_ACTION`.
 */
@Dao
interface RecoveryMarkerDao {

    @Upsert
    suspend fun upsert(marker: RecoveryMarkerEntity)

    @Query("SELECT * FROM recovery_marker")
    fun observeAll(): Flow<List<RecoveryMarkerEntity>>

    @Query("SELECT * FROM recovery_marker WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): RecoveryMarkerEntity?

    @Query("SELECT * FROM recovery_marker WHERE recoverable = 1")
    suspend fun getRecoverable(): List<RecoveryMarkerEntity>

    @Query("UPDATE recovery_marker SET attempts = attempts + 1 WHERE taskId = :taskId")
    suspend fun incrementAttempts(taskId: String): Int

    @Query("DELETE FROM recovery_marker WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}
