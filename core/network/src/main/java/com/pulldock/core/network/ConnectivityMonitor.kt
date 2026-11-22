package com.pulldock.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Observed network posture, used by the queue coordinator and policy enforcement
 * (US7) to decide whether new transfers may start.
 *
 * `metered` follows the platform definition (cellular and user-marked Wi-Fi networks).
 */
data class NetworkStatus(
    /** A validated network exists and data can flow. */
    val online: Boolean,
    /** The active network is metered (cost-sensitive). */
    val metered: Boolean,
    /** The active network is roaming. */
    val roaming: Boolean,
) {
    companion object {
        /** Offline posture used before the first platform callback. */
        val OFFLINE = NetworkStatus(online = false, metered = false, roaming = false)
    }
}

/**
 * Connectivity observation contract. Implementations back this with
 * `ConnectivityManager` network callbacks and always emit the current value first
 * (conflated) so collectors never wait for a change to learn the state.
 */
interface ConnectivityMonitor {

    /** Conflated stream of connectivity status; replays the latest value. */
    val status: Flow<NetworkStatus>

    /** Synchronous snapshot; [NetworkStatus.OFFLINE] before the first observation. */
    fun current(): NetworkStatus
}
