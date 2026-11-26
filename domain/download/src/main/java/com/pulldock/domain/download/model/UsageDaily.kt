package com.pulldock.domain.download.model

import java.time.LocalDate

/** A single day of app usage (data usage + running time). */
data class UsageDaily(
    val day: LocalDate,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val activeTransferMs: Long,
    val appOpenMs: Long,
) {
    val totalBytes: Long get() = wifiRxBytes + wifiTxBytes + mobileRxBytes + mobileTxBytes
    val wifiBytes: Long get() = wifiRxBytes + wifiTxBytes
    val mobileBytes: Long get() = mobileRxBytes + mobileTxBytes
}

/** A pending usage increment to be accumulated into the current day. */
data class UsageDelta(
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L,
    val activeTransferMs: Long = 0L,
    val appOpenMs: Long = 0L,
)

/** Selectable usage ranges; [days] is the look-back window (Month = 30 days). */
enum class UsageRange(val days: Int) {
    DAY(1),
    THREE_DAYS(3),
    WEEK(7),
    MONTH(30),
}