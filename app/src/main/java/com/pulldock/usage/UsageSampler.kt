package com.pulldock.usage

import android.net.TrafficStats
import android.os.SystemClock
import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.core.network.ConnectivityMonitor
import com.pulldock.domain.download.model.UsageDelta
import com.pulldock.domain.download.repository.UsageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Samples TrafficStats deltas and running time, buffering them and flushing to Room on a
 * cadence (not every tick) to avoid write churn. Wi-Fi vs mobile is classified by the current
 * transport (`ConnectivityMonitor.metered`), which is robust to `TrafficStats` returning -1.
 */
@Singleton
class UsageSampler @Inject constructor(
    private val repository: UsageRepository,
    private val connectivity: ConnectivityMonitor,
    private val dispatchers: AppDispatchers,
) {

    private var primed = false
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastElapsed = 0L
    private var pending = UsageDelta()
    private var flushJob: Job? = null

    /** Reads counters and buffers one tick of transfer time + data deltas. */
    @Synchronized
    fun sample() {
        val now = SystemClock.elapsedRealtime()
        val totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)

        val rx: Long
        val tx: Long
        if (!primed || totalRx < lastRx || totalTx < lastTx) {
            rx = 0L
            tx = 0L
        } else {
            rx = totalRx - lastRx
            tx = totalTx - lastTx
        }
        lastRx = totalRx
        lastTx = totalTx
        primed = true

        val activeMs = if (lastElapsed == 0L) 0L else (now - lastElapsed).coerceIn(0L, MAX_TICK_MS)
        lastElapsed = now

        val metered = connectivity.current().metered
        pending = pending.copy(
            wifiRxBytes = pending.wifiRxBytes + if (metered) 0 else rx,
            wifiTxBytes = pending.wifiTxBytes + if (metered) 0 else tx,
            mobileRxBytes = pending.mobileRxBytes + if (metered) rx else 0,
            mobileTxBytes = pending.mobileTxBytes + if (metered) tx else 0,
            activeTransferMs = pending.activeTransferMs + activeMs,
        )
    }

    /** Adds app-open foreground time (called from the Application lifecycle observer). */
    @Synchronized
    fun recordAppOpen(millis: Long) {
        if (millis <= 0) return
        pending = pending.copy(appOpenMs = pending.appOpenMs + millis)
    }

    /** Writes buffered deltas to Room and resets the buffer. */
    suspend fun flush() {
        val snapshot = synchronized(this) {
            val current = pending
            pending = UsageDelta()
            current
        }
        val empty = snapshot.wifiRxBytes == 0L && snapshot.wifiTxBytes == 0L &&
            snapshot.mobileRxBytes == 0L && snapshot.mobileTxBytes == 0L &&
            snapshot.activeTransferMs == 0L && snapshot.appOpenMs == 0L
        if (!empty) repository.addDelta(snapshot)
    }

    /** Starts the periodic flush loop; call once from the Application. */
    fun start(scope: CoroutineScope) {
        flushJob?.cancel()
        flushJob = scope.launch(dispatchers.default) {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 60_000L
        const val MAX_TICK_MS = 60_000L
    }
}