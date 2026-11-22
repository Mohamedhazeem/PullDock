package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentMetadata
import com.pulldock.domain.torrent.TorrentResult
import com.pulldock.domain.torrent.model.FileSelection
import com.pulldock.domain.torrent.model.TorrentSource
import java.time.Duration
import javax.inject.Inject

/**
 * Validates and adds a torrent source (FR-001/FR-002/FR-006). Rejects malformed input
 * with a typed error before any task is created; duplicate sources fail with
 * `DUPLICATE_INFOHASH` instead of creating a second transfer (invariant §3.2).
 */
class AddTorrent @Inject constructor(
    private val repository: DownloadRepository,
    private val coordinator: QueueCoordinator,
) {

    suspend operator fun invoke(source: TorrentSource, destination: StorageDestination): UseCaseResult<DownloadTask> {
        val validation = when (source.kind) {
            TorrentSource.Kind.MAGNET -> MagnetValidator.validate(source.value)
            TorrentSource.Kind.TORRENT_URI -> validateTorrentUri(source.value)
        }
        validation?.let { return UseCaseResult.Failure(it) }

        val existing = repository.findBySourceHash(com.pulldock.domain.download.model.SourceHashing.forTorrent(source))
        if (existing != null && !existing.state.isTerminal) {
            return UseCaseResult.Failure(DownloadError.TorrentEngine(DownloadError.TorrentReason.DUPLICATE_INFOHASH))
        }

        val created = repository.addTorrent(source, destination)
        coordinator.enqueue(created)
        // Report the end state (queued), not the pre-enqueue snapshot.
        return UseCaseResult.of(repository.getTask(created.id)) {
            DownloadError.Persistence(DownloadError.PersistenceReason.DB_ERROR)
        }
    }

    /** Torrent file references must be content/file URIs with a bounded length. */
    private fun validateTorrentUri(value: String): DownloadError? = when {
        value.isBlank() -> DownloadError.Input(DownloadError.InputReason.MALFORMED_TORRENT_FILE)
        !(value.startsWith("content://") || value.startsWith("file://")) ->
            DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_SCHEME)
        value.length > MAX_TORRENT_URI_LENGTH ->
            DownloadError.Input(DownloadError.InputReason.MALFORMED_TORRENT_FILE, "uri too long")
        else -> null
    }

    private companion object {
        const val MAX_TORRENT_URI_LENGTH = 4_096
    }
}

/**
 * Magnet URI validation (FR-002). Pure and dependency-free; used before any engine
 * call. Only `urn:btih:` infohashes (hex or base32) are accepted — unsupported
 * parameter schemes surface as typed input errors rather than engine crashes.
 */
object MagnetValidator {

    private const val MAGNET_SCHEME = "magnet:"
    private const val BTIH_PREFIX = "xt=urn:btih:"
    private val HEX_REGEX = Regex("^[0-9a-fA-F]{40}$")
    private val BASE32_REGEX = Regex("^[A-Za-z2-7]{32}$")
    private const val MAX_MAGNET_LENGTH = 4_096

    /** `null` when valid, else the typed input error. Scheme matching is case-insensitive (RFC 3986). */
    fun validate(magnet: String): DownloadError? = when {
        magnet.isBlank() -> DownloadError.Input(DownloadError.InputReason.MALFORMED_MAGNET, "empty")
        !magnet.startsWith(MAGNET_SCHEME, ignoreCase = true) -> DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_SCHEME)
        magnet.length > MAX_MAGNET_LENGTH -> DownloadError.Input(DownloadError.InputReason.MALFORMED_MAGNET, "too long")
        else -> {
            val lower = magnet.lowercase()
            val index = lower.indexOf(BTIH_PREFIX)
            if (index < 0) {
                DownloadError.Input(DownloadError.InputReason.MALFORMED_MAGNET, "missing urn:btih")
            } else {
                val start = index + BTIH_PREFIX.length
                val end = magnet.indexOfFirstOffset(start) { it == '&' }
                val hash = magnet.substring(start, end)
                if (hash.length == 40 && hash.matches(HEX_REGEX) || hash.length == 32 && hash.matches(BASE32_REGEX)) {
                    null
                } else {
                    DownloadError.Input(DownloadError.InputReason.MALFORMED_MAGNET, "invalid infohash")
                }
            }
        }
    }

    /**
     * Deterministic duplicate key (FR-006/invariant §3.2): magnets dedupe on their
     * lowercase btih so tracker/publisher differences do not create duplicates; torrent
     * file URIs dedupe on the URI itself.
     */
    fun dedupeHash(source: TorrentSource): String = when (source.kind) {
        TorrentSource.Kind.MAGNET -> {
            val lower = source.value.lowercase()
            val index = lower.indexOf(BTIH_PREFIX)
            if (index >= 0) {
                val start = index + BTIH_PREFIX.length
                val end = source.value.indexOfFirstOffset(start) { it == '&' }
                "btih:${lower.substring(start, end)}"
            } else {
                "magnet:${source.value.lowercase()}"
            }
        }
        TorrentSource.Kind.TORRENT_URI -> "file:${source.value}"
    }

    private fun String.indexOfFirstOffset(from: Int, predicate: (Char) -> Boolean): Int {
        for (i in from until length) if (predicate(this[i])) return i
        return length
    }
}

/** Fetches torrent metadata with the engine's timeout policy (FR-003/FR-005). */
class FetchTorrentMetadata @Inject constructor(
    private val repository: DownloadRepository,
    private val engine: TorrentEngine,
) {
    suspend operator fun invoke(taskId: String, timeout: Duration = DEFAULT_TIMEOUT): TorrentResult<TorrentMetadata> {
        val task = repository.getTask(taskId)
            ?: return TorrentResult.Failure(DownloadError.Input(DownloadError.InputReason.INVALID_URL, "unknown task"))
        val engineTaskId = task.engineTaskId
            ?: return TorrentResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        return engine.fetchMetadata(engineTaskId, timeout)
    }

    companion object {
        /** Bounded metadata wait; mapped to METADATA_TIMEOUT on expiry (FR-005). */
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}

/** Applies the user's file selection to a torrent (FR-004). */
class SelectTorrentFiles @Inject constructor(
    private val repository: DownloadRepository,
    private val engine: TorrentEngine,
) {
    suspend operator fun invoke(taskId: String, selection: FileSelection): UseCaseResult<Unit> {
        val task = repository.getTask(taskId)
            ?: return UseCaseResult.Failure(DownloadError.Input(DownloadError.InputReason.INVALID_URL, "unknown task"))
        if (task.state == DownloadState.COMPLETED || task.state == DownloadState.CANCELLED) {
            return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        }
        val engineTaskId = task.engineTaskId
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        return when (val result = engine.selectFiles(engineTaskId, selection)) {
            is TorrentResult.Success -> UseCaseResult.Success(Unit)
            is TorrentResult.Failure -> UseCaseResult.Failure(result.error)
        }
    }
}
