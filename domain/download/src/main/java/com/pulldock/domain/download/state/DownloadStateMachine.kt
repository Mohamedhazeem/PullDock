package com.pulldock.domain.download.state

import com.pulldock.core.model.DownloadState

/**
 * Enforces the legal download-state transitions (data-model.md §2.2/§2.3, invariant
 * §3.3). The domain layer rejects illegal transitions — the database never accepts a
 * state the machine would refuse.
 *
 * Interpretations recorded here (all consistent with the §2.2 table):
 * - `CANCELLED` is reachable by user-cancel from every non-terminal state except
 *   `COMPLETED` (a completed task can only be removed) and `REMOVING` (already leaving).
 * - `PAUSING -> CANCELLED` is legal: a cancel arriving while the engine is settling
 *   must not be dropped.
 * - `VERIFYING/POST_PROCESSING -> CANCELLED` aborts the verification path; the output
 *   is never published (FR-030).
 * - `REMOVING` has no outgoing transitions: removal deletes the row (handled by the
 *   repository inside one transaction), it is not a state change.
 */
object DownloadStateMachine {

    /** All legal transitions except the user-cancel catch-all, keyed by source state. */
    private val legalTransitions: Map<DownloadState, Set<DownloadState>> = mapOf(
        DownloadState.CREATED to setOf(
            DownloadState.VALIDATING,
            // Newly added tasks are accepted by the queue coordinator; `enqueue`
            // performs this transition (state-machine §2.2, data-model.md).
            DownloadState.QUEUED,
        ),
        DownloadState.VALIDATING to setOf(
            DownloadState.RESOLVING,
            DownloadState.AWAITING_FILE_SELECTION,
            DownloadState.FAILED,
        ),
        DownloadState.RESOLVING to setOf(
            DownloadState.AWAITING_FORMAT_SELECTION,
            DownloadState.FAILED,
        ),
        DownloadState.FETCHING_METADATA to setOf(
            DownloadState.AWAITING_FILE_SELECTION,
            DownloadState.FAILED,
        ),
        DownloadState.AWAITING_FILE_SELECTION to setOf(
            DownloadState.QUEUED,
        ),
        DownloadState.AWAITING_FORMAT_SELECTION to setOf(
            DownloadState.QUEUED,
        ),
        DownloadState.QUEUED to setOf(
            DownloadState.STARTING,
        ),
        DownloadState.STARTING to setOf(
            DownloadState.RUNNING,
            DownloadState.FAILED,
        ),
        DownloadState.RUNNING to setOf(
            DownloadState.PAUSING,
            DownloadState.VERIFYING,
            DownloadState.FAILED,
            // Process death: the engine session is gone and the task is re-queued
            // from persisted resume data (T071/T074, US9).
            DownloadState.QUEUED,
        ),
        DownloadState.PAUSING to setOf(
            DownloadState.PAUSED,
        ),
        DownloadState.PAUSED to setOf(
            DownloadState.QUEUED,
            DownloadState.REMOVING,
        ),
        DownloadState.RETRY_WAIT to setOf(
            DownloadState.QUEUED,
        ),
        DownloadState.VERIFYING to setOf(
            DownloadState.POST_PROCESSING,
            DownloadState.COMPLETED,
            DownloadState.FAILED,
        ),
        DownloadState.POST_PROCESSING to setOf(
            DownloadState.COMPLETED,
            DownloadState.FAILED,
        ),
        DownloadState.FAILED to setOf(
            DownloadState.QUEUED,
            DownloadState.REMOVING,
        ),
        DownloadState.NEEDS_PERMISSION to setOf(
            DownloadState.QUEUED,
        ),
        DownloadState.NEEDS_USER_ACTION to setOf(
            DownloadState.QUEUED,
        ),
        DownloadState.BLOCKED to setOf(
            DownloadState.QUEUED,
        ),
        DownloadState.COMPLETED to setOf(
            DownloadState.REMOVING,
        ),
    )

    /**
     * `true` when [from] may transition to [to]. Enforces the table, the user-cancel
     * catch-all ("where legal"), and the §2.3 rules. Removal (FR-007/FR-027) is a
     * universal exit: every state except `REMOVING` itself may enter `REMOVING`.
     */
    fun canTransition(from: DownloadState, to: DownloadState): Boolean {
        // Removal deletes the row; no state may follow REMOVING.
        if (from == DownloadState.REMOVING) return false
        // CANCELLED restart requires a new task (§2.3) — except removal, so a cancelled
        // task can still be cleaned out of the queue.
        if (from == DownloadState.CANCELLED) return to == DownloadState.REMOVING
        // User cancel catch-all: any non-terminal state except COMPLETED.
        if (to == DownloadState.CANCELLED) return true
        // Removal catch-all: any live state may be removed.
        if (to == DownloadState.REMOVING) return true
        return legalTransitions[from]?.contains(to) == true
    }

    /**
     * Returns the target state when legal, or `null` when the transition is illegal —
     * callers persist `null` results as a typed `Persistence` error rather than guessing.
     */
    fun transition(from: DownloadState, to: DownloadState): DownloadState? =
        if (canTransition(from, to)) to else null

    /** States from which a transfer may (re)enter the engine queue. */
    fun canEnterQueue(from: DownloadState): Boolean = canTransition(from, DownloadState.QUEUED)

    /**
     * States that count as "active" for concurrency accounting (FR-028): the task either
     * runs on an engine or is committed to starting one.
     */
    val activeStates: Set<DownloadState> = setOf(
        DownloadState.STARTING,
        DownloadState.RUNNING,
        DownloadState.PAUSING,
        DownloadState.VERIFYING,
        DownloadState.POST_PROCESSING,
    )

    /** States from which pause is meaningful; pause is idempotent for [DownloadState.PAUSED]. */
    fun canPause(from: DownloadState): Boolean = from == DownloadState.RUNNING || from == DownloadState.PAUSED
}
