package com.pulldock.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulldock.core.database.entity.UsageDailyEntity
import kotlinx.coroutines.flow.Flow

/** Per-day usage rows. Accumulation is serialized in the repository (read-modify-write). */
@Dao
interface UsageDailyDao {

    @Upsert
    suspend fun upsert(entity: UsageDailyEntity)

    @Query("SELECT * FROM usage_daily WHERE day = :day")
    suspend fun get(day: String): UsageDailyEntity?

    @Query("SELECT * FROM usage_daily WHERE day >= :sinceDay ORDER BY day ASC")
    fun observeSince(sinceDay: String): Flow<List<UsageDailyEntity>>

    @Query("DELETE FROM usage_daily WHERE day < :cutoffDay")
    suspend fun deleteOlderThan(cutoffDay: String)
}