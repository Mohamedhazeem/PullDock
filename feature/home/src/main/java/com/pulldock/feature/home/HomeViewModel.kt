package com.pulldock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.usecase.ObserveDownloadDetails
import com.pulldock.domain.download.usecase.ObserveDownloadQueue
import com.pulldock.domain.download.usecase.PauseDownload
import com.pulldock.domain.download.usecase.RemoveDownload
import com.pulldock.domain.download.usecase.ResumeDownload
import com.pulldock.domain.download.usecase.RetryDownload
import com.pulldock.domain.download.usecase.UseCaseResult
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Unified transfer screen state (FR-023/US4): one list with filters and pause/resume/delete. */
data class HomeUiState(
    val loading: Boolean = true,
    val filter: QueueFilter = QueueFilter.ALL,
    val tasks: List<DownloadTask> = emptyList(),
    val torrentDetails: Map<String, TorrentTaskDetails> = emptyMap(),
    val pendingDelete: String? = null,
    val actionError: DownloadError? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDownloadQueue: ObserveDownloadQueue,
    observeDownloadDetails: ObserveDownloadDetails,
    private val pauseUc: PauseDownload,
    private val resumeUc: ResumeDownload,
    private val retryUc: RetryDownload,
    private val removeUc: RemoveDownload,
) : ViewModel() {

    private val filter = MutableStateFlow(QueueFilter.ALL)
    private val extras = MutableStateFlow(HomeExtras())

    val uiState: StateFlow<HomeUiState> = observeDownloadQueue(QueueFilter.ALL)
        .combine(filter) { all, selected -> applyFilter(all, selected) }
        .flatMapLatest { tasks ->
            if (tasks.isEmpty()) {
                flowOf(tasks to emptyMap<String, TorrentTaskDetails>())
            } else {
                combine(
                    tasks.map { task ->
                        observeDownloadDetails(task.id).map { details -> task.id to details?.torrent }
                    },
                ) { entries -> tasks to entries.mapNotNull { (id, details) -> details?.let { id to it } }.toMap() }
            }
        }
        .combine(extras) { (tasks, details), extra ->
            HomeUiState(
                loading = false,
                filter = filter.value,
                tasks = tasks,
                torrentDetails = details,
                pendingDelete = extra.pendingDelete,
                actionError = extra.actionError,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFilter(value: QueueFilter) = filter.update { value }
    fun pause(id: String) = launchAction { pauseUc(id) }

    fun resume(id: String) = launchAction { resumeUc(id) }
    fun retry(id: String) = launchAction { retryUc(id) }

    fun requestDelete(id: String) = extras.update { it.copy(pendingDelete = id) }
    fun dismissDelete() = extras.update { it.copy(pendingDelete = null) }
    fun confirmDelete() {
        val id = extras.value.pendingDelete ?: return
        extras.update { it.copy(pendingDelete = null) }
        viewModelScope.launch { removeUc(id, true) }
    }
    fun removeCompleted(id: String) = launchAction { removeUc(id, false) }
    fun consumeActionError() = extras.update { it.copy(actionError = null) }

    private fun launchAction(action: suspend () -> UseCaseResult<Unit>) {
        viewModelScope.launch {
            when (val result = action()) {
                is UseCaseResult.Success -> Unit
                is UseCaseResult.Failure -> extras.update { it.copy(actionError = result.error) }
            }
        }
    }

    private fun applyFilter(all: List<DownloadTask>, selected: QueueFilter): List<DownloadTask> =
        all.filter { task ->
            when (selected) {
                QueueFilter.ALL -> true
                QueueFilter.ACTIVE -> task.state in ACTIVE_STATES
                QueueFilter.QUEUED -> task.state == DownloadState.QUEUED || task.state == DownloadState.RETRY_WAIT
                QueueFilter.PAUSED -> task.state == DownloadState.PAUSED
                QueueFilter.COMPLETED -> task.state == DownloadState.COMPLETED
                QueueFilter.FAILED -> task.state in FAILED_STATES
            }
        }.sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy { it.queueOrder }.thenBy { it.createdAt.toEpochMilli() })

    private data class HomeExtras(
        val pendingDelete: String? = null,
        val actionError: DownloadError? = null,
    )

    private companion object {
        val ACTIVE_STATES = setOf(
            DownloadState.STARTING, DownloadState.RUNNING, DownloadState.PAUSING,
            DownloadState.VERIFYING, DownloadState.POST_PROCESSING,
        )
        val FAILED_STATES = setOf(
            DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION,
            DownloadState.BLOCKED, DownloadState.NEEDS_PERMISSION,
        )
    }
}
