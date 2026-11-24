package com.pulldock

import com.pulldock.data.download.Recovery
import com.pulldock.execution.TransferServiceController
import com.pulldock.execution.MaintenanceJobs
import com.pulldock.execution.TransferNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PullDockApplication : android.app.Application() {

    @Inject lateinit var serviceController: TransferServiceController
    @Inject lateinit var recovery: Recovery

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TransferNotifications(this).ensureChannels()
        // Deferrable jobs only: file reconciliation/cleanup on WorkManager.
        MaintenanceJobs.schedule(this)
        // Engine recovery runs once here, before the coordinator starts dispatching.
        appScope.launch { recovery.recover() }
        // Foreground service follows active transfers (screen off / app switching).
        serviceController.start()
    }
}
