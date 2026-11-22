package com.pulldock.engines.torrent

import org.libtorrent4j.AlertListener
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.FileErrorAlert
import org.libtorrent4j.alerts.MetadataFailedAlert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TorrentRemovedAlert
import org.libtorrent4j.alerts.TorrentAlert

/**
 * Engine-level observations produced by the alert loop. These are libtorrent-shaped
 * but carry no swig/native types (constitution VI); the data-layer adapter translates
 * them into domain `TorrentEvent`s and state transitions.
 */
sealed interface EngineObservation {

    /** The engine accepted an add request for this infohash. */
    data class Added(val infoHashHex: String) : EngineObservation

    /** Metadata (from peers or the torrent file) became available. */
    data class MetadataReady(val infoHashHex: String) : EngineObservation

    /** Metadata never arrived within the engine's window (peer side failed). */
    data class MetadataFailed(val infoHashHex: String) : EngineObservation

    /** All wanted pieces verified: transfer completed. */
    data class Finished(val infoHashHex: String) : EngineObservation

    /** A fatal per-torrent error was reported by the engine. */
    data class Failed(val infoHashHex: String, val message: String) : EngineObservation

    /** A file-level error was reported (disk full, permission, missing). */
    data class FileFailed(val infoHashHex: String, val filename: String) : EngineObservation

    /** The engine produced resume data for this torrent. */
    data class ResumeDataReady(val infoHashHex: String, val bytes: ByteArray, val version: Int) : EngineObservation

    /** The torrent was removed from the session. */
    data class Removed(val infoHashHex: String) : EngineObservation
}

/**
 * Drains libtorrent alerts into typed [EngineObservation]s (T063). One listener
 * receives every alert (`types() = null` per libtorrent4j convention) and the
 * mapping is total — unknown alerts are intentionally ignored rather than guessed.
 *
 * Alerts arrive on the engine's alert thread; the sink implementations marshal to the
 * caller's context (the adapter posts to its own coroutine scope), so this class holds
 * no mutable state.
 */
class AlertLoop(private val sink: (EngineObservation) -> Unit) : AlertListener {

    override fun types(): IntArray? = null // receive every alert type

    override fun alert(alert: Alert<*>) {
        when (alert) {
            is AddTorrentAlert -> alert.handle()?.infoHash()?.let {
                sink(EngineObservation.Added(it.toHex()))
            }
            is MetadataReceivedAlert -> alert.handle()?.infoHash()?.let {
                sink(EngineObservation.MetadataReady(it.toHex()))
            }
            is MetadataFailedAlert -> alert.handle()?.infoHash()?.let {
                sink(EngineObservation.MetadataFailed(it.toHex()))
            }
            is TorrentFinishedAlert -> alert.handle()?.infoHash()?.let {
                sink(EngineObservation.Finished(it.toHex()))
            }
            is TorrentErrorAlert -> alert.handle()?.infoHash()?.let {
                // Sanitized: engine message only, never peer/tracker URLs (FR-049).
                sink(EngineObservation.Failed(it.toHex(), alert.message()))
            }
            is FileErrorAlert -> alert.handle()?.infoHash()?.let {
                sink(EngineObservation.FileFailed(it.toHex(), alert.filename() ?: ""))
            }
            is SaveResumeDataAlert -> {
                val params = alert.params()
                val hash = alert.handle()?.infoHash()
                if (params != null && hash != null) {
                    sink(
                        EngineObservation.ResumeDataReady(
                            infoHashHex = hash.toHex(),
                            bytes = org.libtorrent4j.AddTorrentParams.writeResumeDataBuf(params),
                            version = params.version,
                        ),
                    )
                }
            }
            is TorrentRemovedAlert -> {
                val hash = TorrentAlertHashExtractor.extract(alert)
                if (hash != null) sink(EngineObservation.Removed(hash))
            }
            else -> {
                // Peer connect/disconnect and stats alerts are handled by the
                // adapter's status polling; nothing to translate here.
            }
        }
    }
}

/** TorrentRemovedAlert loses its handle; the infohash is read from the alert message. */
private object TorrentAlertHashExtractor {
    fun extract(alert: TorrentAlert<*>): String? = try {
        alert.handle()?.infoHash()?.toHex()
    } catch (_: Exception) {
        null
    }
}
