package com.pulldock.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.pulldock.core.database.entity.TorrentFileEntity
import com.pulldock.core.database.entity.TorrentTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Torrent detail persistence. File selection updates replace the whole file set in one
 * transaction (data-model.md §4.3), so selection and priorities never tear.
 */
@Dao
interface TorrentTaskDao {

    @Upsert
    suspend fun upsert(task: TorrentTaskEntity)

    @Query("SELECT * FROM torrent_task WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): TorrentTaskEntity?

    @Query("SELECT * FROM torrent_task WHERE taskId = :taskId")
    fun observeByTaskId(taskId: String): Flow<TorrentTaskEntity?>

    @Query("SELECT * FROM torrent_task WHERE infohash = :infohash LIMIT 1")
    suspend fun findByInfohash(infohash: String): TorrentTaskEntity?

    @Query("UPDATE torrent_task SET resumeData = :resumeData, resumeDataVersion = :version WHERE taskId = :taskId")
    suspend fun updateResumeData(taskId: String, resumeData: ByteArray?, version: Int): Int

    @Query(
        """
        UPDATE torrent_task
        SET seedAfterCompletion = :seedAfterCompletion,
            maxUploadRateBytesPerSec = :maxUploadRate,
            stopRatio = :stopRatio,
            timeLimitSeconds = :timeLimitSeconds,
            perTorrentOverride = :perTorrentOverride
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateSeedingPolicy(
        taskId: String,
        seedAfterCompletion: Boolean,
        maxUploadRate: Long?,
        stopRatio: Double?,
        timeLimitSeconds: Long?,
        perTorrentOverride: Boolean,
    ): Int

    @Query("UPDATE torrent_task SET uploadedBytes = :uploadedBytes, peerCount = :peerCount WHERE taskId = :taskId")
    suspend fun updateStats(taskId: String, uploadedBytes: Long, peerCount: Int): Int

    @Query("SELECT * FROM torrent_file WHERE taskId = :taskId ORDER BY fileIndex ASC")
    suspend fun getFiles(taskId: String): List<TorrentFileEntity>

    @Query("SELECT * FROM torrent_file WHERE taskId = :taskId ORDER BY fileIndex ASC")
    fun observeFiles(taskId: String): Flow<List<TorrentFileEntity>>

    /** Replaces the persisted file list atomically. */
    @Transaction
    suspend fun replaceFiles(taskId: String, files: List<TorrentFileEntity>) {
        deleteFiles(taskId)
        files.forEach { insertFile(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: TorrentFileEntity)

    @Query("DELETE FROM torrent_file WHERE taskId = :taskId")
    suspend fun deleteFiles(taskId: String)

    @Query("DELETE FROM torrent_task WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}
