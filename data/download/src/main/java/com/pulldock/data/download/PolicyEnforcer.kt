package com.pulldock.data.download

import com.pulldock.core.model.DownloadState
import com.pulldock.core.network.ConnectivityMonitor
import com.pulldock.core.network.NetworkStatus
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.state.DownloadStateMachine
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Policy enforcement (T106, US7): network posture (Wi-Fi-only / metered) and the
 * battery threshold gate what the scheduler may start. When conditions forbid running,
 * active tasks pause through the legal state-machine path (RUNNING→PAUSING, US7
 * scenario 3: "active downloads are paused and the user is informed"); queued tasks
 * simply do not start until conditions recover — the queue shows them waiting rather
 * than inventing transitions the approved table does not define.
 */
class PolicyEnforcer @Inject constructor(
    private val repository: DownloadRepository,
    private val connectivity: ConnectivityMonitor,
) {

    /** `true` when new downloads may start under the current network posture. */
    suspend fun networkAllows(policy: DownloadPolicy): Boolean {
        val status: NetworkStatus = connectivity.status.first()
        if (!status.online) return false
        if (policy.wifiOnly && status.metered) return false
        if (!policy.meteredAllowed && status.metered) return false
        return true
    }

    /** `true` when the battery level permits transfers (0 disables the check). */
    fun batteryAllows(policy: DownloadPolicy, batteryPercent: Int): Boolean =
        policy.batteryThresholdPercent == 0 || batteryPercent >= policy.batteryThresholdPercent

    /**
     * Applies the policy: pauses (via PAUSING) active tasks when the network/battery
     * forbids them. Returns the number of tasks transitioned. Starting decisions are
     * gated by [networkAllows]/[batteryAllows] inside the scheduler loop.
     */
    suspend fun pauseViolating(tasks: List<DownloadTask>, policy: DownloadPolicy, batteryPercent: Int): Int {
        val mayRun = networkAllows(policy) && batteryAllows(policy, batteryPercent)
        if (mayRun) return 0
        var changed = 0
        tasks
            .filter { it.state == DownloadState.RUNNING }
            .forEach { task ->
                if (repository.updateState(task.id, DownloadState.PAUSING)) changed += 1
            }
        return changed
    }
}
