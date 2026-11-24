package com.pulldock.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pulldock.core.database.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * CRUD + observation for the unified task table. All state transitions flow through
 * [updateState] so `stateSequence` grows monotonically per task (data-model.md §2.3).
 * Filters map the domain `QueueFilter` (FR-023); active states mirror
 * `DownloadStateMachine.activeStates`.
 */
@Dao
interface DownloadTaskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: DownloadTaskEntity)

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_task WHERE id = :id")
    fun observeById(id: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_task ORDER BY priority DESC, queueOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT * FROM download_task
        WHERE state IN ('STARTING', 'RUNNING', 'PAUSING', 'VERIFYING', 'POST_PROCESSING')
        ORDER BY priority DESC, queueOrder ASC, createdAt ASC
        """,
    )
    fun observeActive(): Flow<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT * FROM download_task
        WHERE state IN ('QUEUED', 'STARTING', 'RUNNING', 'PAUSING', 'VERIFYING', 'POST_PROCESSING', 'RETRY_WAIT')
        ORDER BY priority DESC, queueOrder ASC, createdAt ASC
        """,
    )
    fun observeQueuedAndActive(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE state = 'PAUSED' ORDER BY priority DESC, queueOrder ASC")
    fun observePaused(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE state = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT * FROM download_task
        WHERE state IN ('FAILED', 'NEEDS_PERMISSION', 'NEEDS_USER_ACTION', 'BLOCKED')
        ORDER BY updatedAt DESC
        """,
    )
    fun observeFailed(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE sourceHash = :hash LIMIT 1")
    suspend fun findBySourceHash(hash: String): DownloadTaskEntity?

    /** Atomically applies a legal state transition with a new sequence number. */
    @Query(
        """
        UPDATE download_task
        SET state = :state,
            stateSequence = stateSequence + 1,
            errorCode = :errorCode,
            errorDetails = :errorDetails,
            updatedAt = :updatedAt,
            completedAt = CASE WHEN :state = 'COMPLETED' THEN :updatedAt ELSE completedAt END
        WHERE id = :id
        """,
    )
    suspend fun updateState(id: String, state: String, errorCode: String?, errorDetails: String?, updatedAt: Long): Int

    @Query(
        """
        UPDATE download_task
        SET downloadedBytes = :downloadedBytes,
            totalBytes = COALESCE(:totalBytes, totalBytes),
            downloadSpeed = :downloadSpeed,
            etaSeconds = :etaSeconds,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        downloadSpeed: Long,
        etaSeconds: Long?,
        updatedAt: Long,
    ): Int

    @Query("UPDATE download_task SET retryCount = :count, nextRetryAt = :nextRetryAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRetry(id: String, count: Int, nextRetryAt: Long?, updatedAt: Long): Int

    @Query("UPDATE download_task SET engineTaskId = :engineTaskId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEngineTaskId(id: String, engineTaskId: String?, updatedAt: Long): Int

    /** Fills the display name/size once torrent metadata arrives (FR-003). */
    @Query(
        """
        UPDATE download_task
        SET displayName = :displayName,
            totalBytes = COALESCE(:totalBytes, totalBytes),
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateMetadata(id: String, displayName: String, totalBytes: Long?, updatedAt: Long): Int

    /** Verified upload rate for torrent telemetry (FR-044 / US4). */
    @Query("UPDATE download_task SET uploadSpeed = :speed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUploadSpeed(id: String, speed: Long?, updatedAt: Long): Int

    /** Publishes output and completes a verified transfer in one guarded write (FR-030/§4.3). */
    @Query(
        """
        UPDATE download_task
        SET state = 'COMPLETED',
            stateSequence = stateSequence + 1,
            outputUri = :outputUri,
            outputFilename = :filename,
            completedAt = :completedAt,
            updatedAt = :completedAt
        WHERE id = :id AND state = 'VERIFYING'
        """,
    )
    suspend fun completeWithOutput(id: String, outputUri: String, filename: String, completedAt: Long): Int

    @Query(
        """
        UPDATE download_task
        SET limitsDownloadRate = :downloadRate, limitsUploadRate = :uploadRate,
            limitsMaxConnections = :maxConnections, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateLimits(id: String, downloadRate: Long?, uploadRate: Long?, maxConnections: Int?, updatedAt: Long): Int

    /** Reorders by writing fresh queueOrder values; runs in one transaction. */
    @Transaction
    suspend fun reorder(ids: List<String>) {
        ids.forEachIndexed { index, id ->
            updateQueueOrder(id, index)
        }
    }

    @Query("UPDATE download_task SET queueOrder = :order, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateQueueOrder(id: String, order: Int, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun delete(id: String)
}
