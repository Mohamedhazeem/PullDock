package com.pulldock.feature.queue

import com.pulldock.core.model.DownloadError
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter

/**
 * Queue UI state (T045/T050): filter selection, task list, per-task action intents.
 * Filters mirror FR-023; item actions are expressed as data so the screen stays dumb
 * and the ViewModel owns the guards.
 */
data class QueueUiState(
    val loading: Boolean = true,
    val filter: QueueFilter = QueueFilter.ALL,
    val tasks: List<DownloadTask> = emptyList(),
    /** Task id pending user confirmation for removal (destructive, FR-041). */
    val pendingRemoval: String? = null,
    /** Transient action error surfaced as a snackbar message. */
    val actionError: DownloadError? = null,
) {
    /** A removal dialog is showing. */
    val showingRemovalConfirmation: Boolean get() = pendingRemoval != null
}
