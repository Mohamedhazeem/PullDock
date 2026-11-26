package com.pulldock

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import com.pulldock.data.download.Recovery
import com.pulldock.execution.TransferServiceController
import com.pulldock.execution.MaintenanceJobs
import com.pulldock.execution.TransferNotifications
import com.pulldock.usage.UsageSampler
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
    @Inject lateinit var usageSampler: UsageSampler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivities = 0
    private var foregroundSince = 0L

    override fun onCreate() {
        super.onCreate()
        TransferNotifications(this).ensureChannels()
        // Deferrable jobs only: file reconciliation/cleanup on WorkManager.
        MaintenanceJobs.schedule(this)
        // Engine recovery runs once here, before the coordinator starts dispatching.
        appScope.launch { recovery.recover() }
        // Foreground service follows active transfers (screen off / app switching).
        serviceController.start()
        // Usage: periodic flush + app-open (foreground) time tracking.
        usageSampler.start(appScope)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) foregroundSince = SystemClock.elapsedRealtime()
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0 && foregroundSince > 0L) {
                    val elapsed = SystemClock.elapsedRealtime() - foregroundSince
                    foregroundSince = 0L
                    usageSampler.recordAppOpen(elapsed)
                    appScope.launch { runCatching { usageSampler.flush() } }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
