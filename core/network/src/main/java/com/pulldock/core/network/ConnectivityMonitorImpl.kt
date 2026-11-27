package com.pulldock.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ConnectivityManager-backed observation (T008 impl). Emits the current posture
 * immediately and replays changes via network callbacks; conflation bounds the rate
 * (plan.md §Performance Design). The callback registration lives on the application
 * context — no Activity references, no leaks (constitution IV).
 */
@Singleton
class ConnectivityMonitorImpl @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : ConnectivityMonitor {

    private val latest = MutableStateFlow(currentNetworkStatus())

    // Register the platform callback exactly once (in init) and expose the sticky state.
    // Re-registering per collector spammed the system with repeated NetworkRequest logs.
    init {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
            override fun onLost(network: Network) = refresh()
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // Callback registration unsupported on this platform; the sticky value still works.
        }
    }

    override val status: Flow<NetworkStatus> = latest.asStateFlow()

    override fun current(): NetworkStatus = latest.value.also { refresh() }

    private fun refresh() {
        val status = currentNetworkStatus()
        if (status != latest.value) latest.value = status
    }

    private fun currentNetworkStatus(): NetworkStatus = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return NetworkStatus.OFFLINE
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkStatus.OFFLINE
        NetworkStatus(
            online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            roaming = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING).not(),
        )
    }.getOrDefault(NetworkStatus.OFFLINE)
}

@Module
@InstallIn(SingletonComponent::class)
object ConnectivityModule {

    @Provides
    @Singleton
    fun provideConnectivityMonitor(impl: ConnectivityMonitorImpl): ConnectivityMonitor = impl
}
