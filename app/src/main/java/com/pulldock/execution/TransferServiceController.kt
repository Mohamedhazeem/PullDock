package com.pulldock.execution

import android.content.Context
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.usecase.ObserveDownloadQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the `dataSync` foreground service alive exactly while transfers are active
 * (ADR-0005). Without this controller the service class existed but was never started,
 * so the process had no background protection while the screen was off or another app
 * was in front.
 */
@Singleton
class TransferServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeQueue: ObserveDownloadQueue,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serviceActive = false

    /** Starts observing queue state; safe to call once from Application.onCreate. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            observeQueue(QueueFilter.ACTIVE).collectLatest { tasks ->
                val shouldRun = tasks.isNotEmpty()
                when {
                    shouldRun && !serviceActive -> {
                        TransferService.start(context)
                        serviceActive = true
                    }
                    !shouldRun && serviceActive -> {
                        TransferService.stop(context)
                        serviceActive = false
                    }
                }
            }
        }
    }

    private var started: Boolean = false
}
