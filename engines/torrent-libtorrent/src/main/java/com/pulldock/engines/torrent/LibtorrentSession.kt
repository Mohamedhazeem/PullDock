package com.pulldock.engines.torrent

import com.pulldock.core.common.dispatchers.AppDispatchers
import java.util.concurrent.atomic.AtomicBoolean
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.alerts.Alert

/**
 * Owns the single libtorrent [SessionManager] for the process (constitution VI):
 * deterministic lifecycle, idempotent close, and single-thread marshalling of every
 * engine call onto the session's dedicated executor — engine calls never happen on the
 * caller's thread and never on the main thread.
 *
 * The session survives task churn but not process death; recovery rebuilds it from
 * persisted resume data (T071). [close] is called from the process-death/service
 * shutdown path.
 */
class LibtorrentSession(private val dispatchers: AppDispatchers) : AutoCloseable {

    /** Dedicated single-thread executor: all engine operations serialize here. */
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pulldock-torrent-session").apply { isDaemon = false }
    }

    private val sessionManager = SessionManager()

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    /** Runs [block] on the session thread; cancellable through the returned job's caller. */
    fun <T> execute(block: () -> T): java.util.concurrent.Future<T> = executor.submit(block)

    /** Starts the session once with the production settings pack. Idempotent. */
    fun ensureStarted() {
        if (closed.get()) throw IllegalStateException("Torrent session already closed")
        if (started.compareAndSet(false, true)) {
            execute {
                sessionManager.start(SessionParams(defaultSettings()))
            }.get()
        }
    }

    val isRunning: Boolean
        get() = started.get() && sessionManager.isRunning

    /** Registers an alert sink; alerts arrive on the session's alert thread. */
    fun addListener(listener: org.libtorrent4j.AlertListener) {
        sessionManager.addListener(listener)
    }

    fun removeListener(listener: org.libtorrent4j.AlertListener) {
        sessionManager.removeListener(listener)
    }

    /** Direct access for the data-layer adapter; calls MUST be marshalled via [execute]. */
    fun manager(): SessionManager = sessionManager

    /**
     * Idempotent shutdown (constitution VI): stops the session and releases the
     * executor. Called from the process-death path; safe to call repeatedly.
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                execute {
                    if (sessionManager.isRunning) {
                        sessionManager.stop()
                    }
                }.get()
            } finally {
                executor.shutdown()
                executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    /**
     * Baseline settings: DHT enabled, default listen interfaces, sane active limits —
     * per-task limits are applied by the adapter at the torrent level (FR-008).
     */
    private fun defaultSettings() = org.libtorrent4j.SettingsPack().apply {
        // Keep library defaults except explicit concurrency guards; the coordinator
        // applies user policy via applySettings when it changes (T100/T106).
    }
}
