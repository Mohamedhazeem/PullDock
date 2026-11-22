package com.pulldock.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulldock.core.database.entity.ExtractorTaskEntity
import kotlinx.coroutines.flow.Flow

/** Extractor detail persistence (FR-014/FR-016 task metadata). */
@Dao
interface ExtractorTaskDao {

    @Upsert
    suspend fun upsert(task: ExtractorTaskEntity)

    @Query("SELECT * FROM extractor_task WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): ExtractorTaskEntity?

    @Query("SELECT * FROM extractor_task WHERE taskId = :taskId")
    fun observeByTaskId(taskId: String): Flow<ExtractorTaskEntity?>

    @Query("UPDATE extractor_task SET processingStatus = :status, providerError = :providerError WHERE taskId = :taskId")
    suspend fun updateProcessing(taskId: String, status: String, providerError: String?): Int

    @Query("DELETE FROM extractor_task WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}
