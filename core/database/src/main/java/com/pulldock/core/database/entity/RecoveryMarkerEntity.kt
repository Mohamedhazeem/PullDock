package com.pulldock.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Interrupted-task recovery marker (data-model.md §4.5, FR-029/FR-034). Written
 * atomically with task state transitions; on startup the coordinator either restores
 * the engine session from resume data or marks the task `NEEDS_USER_ACTION` — never
 * silently drops it (US9 scenario 2).
 */
@Entity(
    tableName = "recovery_marker",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recoverable"])],
)
data class RecoveryMarkerEntity(
    @PrimaryKey @ColumnInfo(name = "taskId") val taskId: String,
    @ColumnInfo(name = "interruptedAt") val interruptedAt: Long,
    /** Typed reason code (sanitized; data-model.md §4.7). */
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "resumeDataPresent") val resumeDataPresent: Boolean,
    @ColumnInfo(name = "recoverable") val recoverable: Boolean,
    @ColumnInfo(name = "attempts") val attempts: Int,
)
