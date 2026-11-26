package com.pulldock.execution

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pulldock.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.data.download.PolicyEnforcer
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.repository.NetworkPolicyRepository
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.usage.UsageSampler

/**
 * `dataSync` foreground service for API < 34 (T108, ADR-0005, FR-031/FR-032):
 * hosts long transfers while backgrounded with a meaningful, controllable ongoing
 * notification. Android 15 gives `dataSync` a 6-hour budget per 24 hours; the service
 * honors `onTimeout` by stopping promptly and leaving persisted state recoverable —
 * no promise of unlimited background execution (FR-035, constitution VIII).
 */
@AndroidEntryPoint
class TransferService : Service() {

    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var policyRepository: NetworkPolicyRepository
    @Inject lateinit var policyEnforcer: PolicyEnforcer
    @Inject lateinit var dispatchers: AppDispatchers
    @Inject lateinit var notifications: TransferNotifications
    @Inject lateinit var usageSampler: UsageSampler

    private lateinit var scope: CoroutineScope
    private var enforcementJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Hilt injects fields in onCreate's generated super call; scope must be built after.
        scope = CoroutineScope(SupervisorJob() + dispatchers.default)
        startForeground(
            TransferNotifications.TRANSFER_NOTIFICATION_ID,
            notifications.buildOngoing(
                getString(R.string.transfer_notification_title),
                getString(R.string.transfer_notification_running),
            ),
        )
        enforcementJob = scope.launch {
            while (isActive) {
                usageSampler.sample()
                val policy: DownloadPolicy = policyRepository.current()
                val tasks = repository.observeQueue(QueueFilter.ALL).first()
                policyEnforcer.pauseViolating(tasks, policy, batteryPercent())
                delay(POLICY_TICK_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return android.app.Service.START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Android 15 dataSync budget exhausted: stop promptly (ADR-0005, FR-031). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        enforcementJob?.cancel()
        // Best-effort final flush on a detached scope so the pending deltas are persisted
        // even though the service scope is being cancelled.
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            runCatching { usageSampler.flush() }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun batteryPercent(): Int {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val intent = registerReceiver(null, filter) ?: return Int.MAX_VALUE
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) Int.MAX_VALUE else (level * 100) / scale
    }

    companion object {
        const val ACTION_STOP = "com.pulldock.execution.TRANSFER_STOP"
        private const val POLICY_TICK_MS = 5_000L

        /** Starts/stops the service; no-op when the platform forbids it (documented). */
        fun start(context: Context) {
            val intent = Intent(context, TransferService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, TransferService::class.java).setAction(ACTION_STOP)

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }
    }
}
