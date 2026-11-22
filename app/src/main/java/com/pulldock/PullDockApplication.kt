package com.pulldock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PullDockApplication : android.app.Application() {

    override fun onCreate() {
        super.onCreate()
        com.pulldock.execution.TransferNotifications(this).ensureChannels()
        // Deferrable jobs only: recovery/reconciliation/cleanup on WorkManager.
        com.pulldock.execution.MaintenanceJobs.schedule(this)
    }
}
