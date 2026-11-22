package com.pulldock.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.usecase.CancelDownload
import com.pulldock.domain.download.usecase.ObserveDownloadQueue
import com.pulldock.domain.download.usecase.PauseDownload
import com.pulldock.domain.download.usecase.RemoveDownload
import com.pulldock.domain.download.usecase.ResumeDownload
import com.pulldock.domain.download.usecase.RetryDownload
import com.pulldock.domain.download.usecase.UseCaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unified queue ViewModel (T045/T050, US4): one list for both engine types, filters,
 * and per-task actions that stay independent of unrelated tasks (SC-005).
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    observeDownloadQueue: ObserveDownloadQueue,
    private val pauseUc: PauseDownload,
    private val resumeUc: ResumeDownload,
    private val cancelUc: CancelDownload,
    private val retryUc: RetryDownload,
    private val removeUc: RemoveDownload,
) : ViewModel() {

    private val filter = MutableStateFlow(QueueFilter.ALL)

    private val extra = MutableStateFlow(QueueExtras())

    val uiState: StateFlow<QueueUiState> =
        combine(observeDownloadQueue(QueueFilter.ALL), filter, extra) { all, selected, extras ->
            QueueUiState(
                loading = false,
                filter = selected,
                tasks = applyFilter(all, selected),
                pendingRemoval = extras.pendingRemoval,
                actionError = extras.actionError,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState())

    fun onFilterChanged(filter: QueueFilter) {
        this.filter.value = filter
    }

    fun pause(id: String) = launchAction(id) { pauseUc(id) }
    fun resume(id: String) = launchAction(id) { resumeUc(id) }
    fun retry(id: String) = launchAction(id) { retryUc(id) }
    fun cancel(id: String) = launchAction(id) { cancelUc(id) }

    /** Requests removal; the screen shows the confirmation dialog first (FR-041). */
    fun requestRemove(id: String) {
        extra.update { it.copy(pendingRemoval = id) }
    }

    /** Dismisses the removal dialog without any change. */
    fun dismissRemove() {
        extra.update { it.copy(pendingRemoval = null) }
    }

    /** Confirmed removal; deleteFile is the distinct file operation (FR-027). */
    fun confirmRemove(deleteFile: Boolean) {
        val id = extra.value.pendingRemoval ?: return
        extra.update { it.copy(pendingRemoval = null) }
        launchAction(id) { removeUc(id, deleteFile) }
    }

    fun consumeActionError() {
        extra.update { it.copy(actionError = null) }
    }

    private fun applyFilter(all: List<DownloadTask>, filter: QueueFilter): List<DownloadTask> = when (filter) {
        QueueFilter.ALL -> all
        QueueFilter.ACTIVE -> all.filter { it.state in ActiveStates }
        QueueFilter.QUEUED -> all.filter { it.state == com.pulldock.core.model.DownloadState.QUEUED || it.state == com.pulldock.core.model.DownloadState.RETRY_WAIT }
        QueueFilter.PAUSED -> all.filter { it.state == com.pulldock.core.model.DownloadState.PAUSED }
        QueueFilter.COMPLETED -> all.filter { it.state == com.pulldock.core.model.DownloadState.COMPLETED }
        QueueFilter.FAILED -> all.filter {
            it.state == com.pulldock.core.model.DownloadState.FAILED ||
                it.state == com.pulldock.core.model.DownloadState.NEEDS_USER_ACTION ||
                it.state == com.pulldock.core.model.DownloadState.BLOCKED ||
                it.state == com.pulldock.core.model.DownloadState.NEEDS_PERMISSION
        }
    }.sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy { it.queueOrder }.thenBy { it.createdAt.toEpochMilli() })

    private fun launchAction(id: String, action: suspend () -> UseCaseResult<Unit>): Unit {
        viewModelScope.launch {
            when (val result = action()) {
                is UseCaseResult.Success -> Unit
                is UseCaseResult.Failure -> extra.update { it.copy(actionError = result.error) }
            }
        }
    }

    private data class QueueExtras(
        val pendingRemoval: String? = null,
        val actionError: com.pulldock.core.model.DownloadError? = null,
    )

    private companion object {
        val ActiveStates = setOf(
            com.pulldock.core.model.DownloadState.STARTING,
            com.pulldock.core.model.DownloadState.RUNNING,
            com.pulldock.core.model.DownloadState.PAUSING,
            com.pulldock.core.model.DownloadState.VERIFYING,
            com.pulldock.core.model.DownloadState.POST_PROCESSING,
        )
    }
}
