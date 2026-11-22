package com.pulldock.execution

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pulldock.core.database.dao.RecoveryMarkerDao
import com.pulldock.data.download.Recovery
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Boot recovery (T110, US9 scenario 3, FR-034, ADR-0005): restores persisted queue
 * state and marks interrupted tasks for recovery — it NEVER auto-starts a restricted
 * service (Android 15 blocks `dataSync` from BOOT_COMPLETED). Work resumes only via
 * user action or the deferrable WorkManager path below.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var recovery: Recovery

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Mark interrupted tasks; no engine session starts here.
                recovery.recover()
            } finally {
                pending.finish()
            }
        }
    }
}
