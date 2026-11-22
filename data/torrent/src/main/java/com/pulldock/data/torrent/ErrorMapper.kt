package com.pulldock.data.torrent

import com.pulldock.core.model.DownloadError
import com.pulldock.engines.torrent.EngineObservation

/**
 * Maps engine observations and failure shapes to the typed error hierarchy (T072).
 * Messages are sanitized: engine text is bounded and never includes peer/tracker URLs
 * (FR-049). Mapping is total for the observation set; unknown shapes stay internal.
 */
object ErrorMapper {

    private const val MAX_DETAIL_LENGTH = 200

    fun fromObservation(observation: EngineObservation): DownloadError = when (observation) {
        is EngineObservation.Failed -> DownloadError.TorrentEngine(
            reason = reasonForEngineMessage(observation.message),
            detail = sanitize(observation.message),
        )
        is EngineObservation.FileFailed -> DownloadError.Storage(
            DownloadError.StorageReason.WRITE_FAILED,
            sanitize(observation.filename),
        )
        is EngineObservation.MetadataFailed -> DownloadError.TorrentEngine(
            DownloadError.TorrentReason.METADATA_TIMEOUT,
        )
        else -> DownloadError.Internal("unmapped engine observation")
    }

    /** Classifies engine error text into torrent failure reasons. */
    fun reasonForEngineMessage(message: String): DownloadError.TorrentReason {
        val lowered = message.lowercase()
        return when {
            "no peers" in lowered || "no connections" in lowered -> DownloadError.TorrentReason.NO_PEERS
            "tracker" in lowered && ("fail" in lowered || "error" in lowered || "timeout" in lowered) ->
                DownloadError.TorrentReason.NO_TRACKERS
            "hash" in lowered || "piece" in lowered || "corrupt" in lowered ->
                DownloadError.TorrentReason.CORRUPT_PIECE
            "resume" in lowered || "fastresume" in lowered -> DownloadError.TorrentReason.INVALID_RESUME_DATA
            "duplicate" in lowered || "already" in lowered -> DownloadError.TorrentReason.DUPLICATE_INFOHASH
            else -> DownloadError.TorrentReason.CORRUPT_PIECE
        }
    }

    /** Bounds engine diagnostics; detail is advisory only and never user-facing raw. */
    fun sanitize(text: String?): String? =
        text?.take(MAX_DETAIL_LENGTH)?.replace(Regex("https?://\\S+"), "<url>")

    /** Maps adapter exceptions to typed errors; cancellation always propagates. */
    fun mapThrowable(throwable: Throwable): DownloadError = when (throwable) {
        is kotlinx.coroutines.CancellationException -> throw throwable
        is IllegalArgumentException ->
            DownloadError.Input(DownloadError.InputReason.MALFORMED_MAGNET)
        is java.io.IOException -> DownloadError.Storage(DownloadError.StorageReason.WRITE_FAILED)
        else -> DownloadError.TorrentEngine(
            DownloadError.TorrentReason.CORRUPT_PIECE,
            sanitize(throwable.message),
        )
    }
}
