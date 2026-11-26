package com.pulldock.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One day of app usage (data-model §usage). Keyed by local calendar day (`yyyy-MM-dd`).
 * Byte counters are per-day deltas accumulated from TrafficStats sampling; durations are
 * active-transfer / app-open milliseconds. Rows older than 30 days are pruned.
 */
@Entity(tableName = "usage_daily")
data class UsageDailyEntity(
    @PrimaryKey @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "wifiRxBytes") val wifiRxBytes: Long = 0L,
    @ColumnInfo(name = "wifiTxBytes") val wifiTxBytes: Long = 0L,
    @ColumnInfo(name = "mobileRxBytes") val mobileRxBytes: Long = 0L,
    @ColumnInfo(name = "mobileTxBytes") val mobileTxBytes: Long = 0L,
    @ColumnInfo(name = "activeTransferMs") val activeTransferMs: Long = 0L,
    @ColumnInfo(name = "appOpenMs") val appOpenMs: Long = 0L,
)