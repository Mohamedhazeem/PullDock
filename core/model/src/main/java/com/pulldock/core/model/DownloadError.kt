package com.pulldock.core.model

/**
 * Centralized, typed error hierarchy for every download-related failure.
 *
 * Presentation maps these to user messages; the `code` value is persisted with the task
 * (`download_task.errorCode`), so it is a durable contract. `detail` is always optional,
 * sanitized diagnostics: never URLs with credentials, tokens, cookies, raw engine alerts,
 * or raw subprocess output (constitution IX).
 */
sealed interface DownloadError {

    /** Stable, persisted, user-mappable error code (e.g. `INPUT_MALFORMED_MAGNET`). */
    val code: String

    /** Untrusted input failed validation before any transfer started. */
    data class Input(val reason: InputReason, val detail: String? = null) : DownloadError {
        override val code: String = "INPUT_${reason.name}"
    }

    /** Network-level failure or a policy restriction on the current network. */
    data class Network(val reason: NetworkReason, val detail: String? = null) : DownloadError {
        override val code: String = "NETWORK_${reason.name}"
    }

    /** Torrent engine failure (metadata, peers, integrity, duplicates). */
    data class TorrentEngine(val reason: TorrentReason, val detail: String? = null) : DownloadError {
        override val code: String = "TORRENT_${reason.name}"
    }

    /** Extractor engine failure (provider, runtime, authorization). */
    data class ExtractorEngine(val reason: ExtractorReason, val detail: String? = null) : DownloadError {
        override val code: String = "EXTRACTOR_${reason.name}"
    }

    /** Storage failure (space, volumes, SAF, filenames, finalize). */
    data class Storage(val reason: StorageReason, val detail: String? = null) : DownloadError {
        override val code: String = "STORAGE_${reason.name}"
    }

    /** A required permission was denied or revoked. */
    data class Permission(val reason: PermissionReason, val detail: String? = null) : DownloadError {
        override val code: String = "PERMISSION_${reason.name}"
    }

    /** Media processing failure (FFmpeg exit, merge, probe, verification). */
    data class Processing(val reason: ProcessingReason, val detail: String? = null) : DownloadError {
        override val code: String = "PROCESSING_${reason.name}"
    }

    /** Persistence failure (database, migration, recovery metadata). */
    data class Persistence(val reason: PersistenceReason, val detail: String? = null) : DownloadError {
        override val code: String = "PERSISTENCE_${reason.name}"
    }

    /** Android lifecycle failure (service timeout, process death handling). */
    data class Lifecycle(val reason: LifecycleReason, val detail: String? = null) : DownloadError {
        override val code: String = "LIFECYCLE_${reason.name}"
    }

    /** Unexpected internal failure. `detail` is sanitized; never surfaced raw to users. */
    data class Internal(val detail: String? = null) : DownloadError {
        override val code: String = "INTERNAL"
    }

    /** Reasons an input was rejected before validation succeeded. */
    enum class InputReason {
        INVALID_URL,
        UNSUPPORTED_SCHEME,
        MALFORMED_MAGNET,
        MALFORMED_TORRENT_FILE,
        UNSUPPORTED_PROVIDER,
    }

    /** Reasons a transfer could not use or continue on the current network. */
    enum class NetworkReason {
        OFFLINE,
        DNS_FAILURE,
        TLS_FAILURE,
        TIMEOUT,
        METERED_BLOCKED,
        ROAMING_BLOCKED,
        CAPTIVE_PORTAL,
    }

    /** Torrent-engine failure reasons mapped from engine alerts. */
    enum class TorrentReason {
        METADATA_TIMEOUT,
        NO_TRACKERS,
        NO_PEERS,
        CORRUPT_PIECE,
        DUPLICATE_INFOHASH,
        INVALID_RESUME_DATA,
        STREAMING_UNAVAILABLE,
    }

    /** Extractor/provider failure reasons (provider health is tracked separately). */
    enum class ExtractorReason {
        UNSUPPORTED,
        PROVIDER_CHANGED,
        RATE_LIMITED,
        EXPIRED_URL,
        CONTENT_REMOVED,
        AUTHORIZATION_REQUIRED,
        RUNTIME_INCOMPATIBLE,
        NO_FORMATS,
    }

    /** Storage failure reasons. */
    enum class StorageReason {
        INSUFFICIENT_SPACE,
        VOLUME_MISSING,
        VOLUME_UNMOUNTED,
        SAF_PERMISSION_REVOKED,
        FILE_EXISTS,
        UNSUPPORTED_EXTENSION,
        INVALID_FILENAME,
        WRITE_FAILED,
        INTERRUPTED_WRITE,
        FINALIZE_FAILED,
    }

    /** Permission failure reasons. */
    enum class PermissionReason {
        NOTIFICATION_DENIED,
        NETWORK_STATE_DENIED,
        REVOKED,
    }

    /** Media-processing failure reasons. */
    enum class ProcessingReason {
        FFMPEG_EXIT,
        MERGE_FAILED,
        PROBE_FAILED,
        VERIFICATION_FAILED,
    }

    /** Persistence failure reasons. */
    enum class PersistenceReason {
        DB_ERROR,
        MIGRATION_FAILED,
        RECOVERY_DATA_INVALID,
    }

    /** Lifecycle failure reasons. */
    enum class LifecycleReason {
        SERVICE_TIMEOUT,
        PROCESS_DEATH,
        CANCELLED,
    }
}
