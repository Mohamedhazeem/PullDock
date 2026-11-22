package com.pulldock.core.model

/**
 * Every state a download task can occupy.
 *
 * The legal transitions between these states are owned by the domain state machine
 * (`domain:download`, data-model.md §2.2); this enum is the shared vocabulary. All values
 * are persisted in `download_task.state`, so names are part of the durable contract and
 * must not be renamed casually.
 */
enum class DownloadState {
    /** Task record created; nothing validated yet. */
    CREATED,

    /** Input (magnet, torrent file, media URL) is being validated. */
    VALIDATING,

    /** Media URL is being resolved against a provider. */
    RESOLVING,

    /** Torrent metadata (name, size, file list) is being fetched. */
    FETCHING_METADATA,

    /** Waiting for the user to select torrent files. */
    AWAITING_FILE_SELECTION,

    /** Waiting for the user to choose a media format. */
    AWAITING_FORMAT_SELECTION,

    /** Ready and waiting for the coordinator to schedule it. */
    QUEUED,

    /** Coordinator dispatched the task; the engine session is starting. */
    STARTING,

    /** Transfer actively running. */
    RUNNING,

    /** Pause requested; engine is settling. Idempotent pause target. */
    PAUSING,

    /** Transfer paused; progress preserved. */
    PAUSED,

    /** Waiting out a retry backoff before returning to the queue. */
    RETRY_WAIT,

    /** Transfer finished; output is being verified before completion. */
    VERIFYING,

    /** Merge/finalize processing is running. Never reported as completed here. */
    POST_PROCESSING,

    /** Verified, finalized, and published. Terminal until removed. */
    COMPLETED,

    /** Transfer failed with a typed error; retry may return it to the queue. */
    FAILED,

    /** User cancelled. A cancelled task is never silently restarted. */
    CANCELLED,

    /** Removal in progress; task and engine detail rows go away together. */
    REMOVING,

    /** A required permission is missing and the task cannot proceed. */
    NEEDS_PERMISSION,

    /** Engine state could not be reconstructed; explicit user action is required. */
    NEEDS_USER_ACTION,

    /** Blocked by user policy (network/battery/storage); unblocks to the queue. */
    BLOCKED,
    ;

    /**
     * A task in a terminal state cannot transition anywhere except removal. `FAILED` is
     * deliberately not terminal (retry returns it to `QUEUED`); `CANCELLED` is terminal
     * because restarting requires a new task.
     */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == REMOVING
}
