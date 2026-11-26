package com.pulldock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadGroup
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.usecase.ObserveDownloadDetails
import com.pulldock.domain.download.usecase.ObserveDownloadQueue
import com.pulldock.domain.download.usecase.ObserveGroups
import com.pulldock.domain.download.usecase.PauseDownload
import com.pulldock.domain.download.usecase.RemoveDownload
import com.pulldock.domain.download.usecase.RemoveGroup
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

/** A group of transfers shown as one expandable card (playlist / multi-file torrent). */
data class HomeGroup(
    val group: DownloadGroup,
    val tasks: List<DownloadTask>,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val state: DownloadState,
)

/** Unified transfer screen state (FR-023/US4): one list with filters and pause/resume/delete. */
data class HomeUiState(
    val loading: Boolean = true,
    val filter: QueueFilter = QueueFilter.ALL,
    /** Ungrouped tasks (standalone transfers). */
    val tasks: List<DownloadTask> = emptyList(),
    /** Grouped transfers (playlists, multi-file torrents). */
    val groups: List<HomeGroup> = emptyList(),
    val torrentDetails: Map<String, TorrentTaskDetails> = emptyMap(),
    val pendingDelete: String? = null,
    val pendingGroupDelete: String? = null,
    val actionError: DownloadError? = null,
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && groups.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDownloadQueue: ObserveDownloadQueue,
    observeDownloadDetails: ObserveDownloadDetails,
    observeGroups: ObserveGroups,
    private val pauseUc: PauseDownload,
    private val resumeUc: ResumeDownload,
    private val retryUc: RetryDownload,
    private val removeUc: RemoveDownload,
    private val removeGroupUc: RemoveGroup,
) : ViewModel() {

    private val filter = MutableStateFlow(QueueFilter.ALL)
    private val extras = MutableStateFlow(HomeExtras())

    val uiState: StateFlow<HomeUiState> = observeDownloadQueue(QueueFilter.ALL)
        .combine(filter) { all, selected -> applyFilter(all, selected) }
        .combine(observeGroups()) { tasks, groups -> tasks to groups }
        .flatMapLatest { (tasks, groups) ->
            if (tasks.isEmpty()) {
                flowOf(HomeLoaded(tasks, groups, emptyMap()))
            } else {
                combine(
                    tasks.map { task ->
                        observeDownloadDetails(task.id).map { details -> task.id to details?.torrent }
                    },
                ) { entries -> HomeLoaded(tasks, groups, entries.mapNotNull { (id, details) -> details?.let { id to it } }.toMap()) }
            }
        }
        .combine(extras) { loaded, extra ->
            val grouped = loaded.tasks.filter { it.groupId != null }
            val ungrouped = loaded.tasks.filter { it.groupId == null }
            val groups = loaded.groups.mapNotNull { group ->
                val children = grouped.filter { it.groupId == group.id }
                if (children.isEmpty()) null else group.toHomeGroup(children)
            }
            HomeUiState(
                loading = false,
                filter = filter.value,
                tasks = ungrouped,
                groups = groups,
                torrentDetails = loaded.details,
                pendingDelete = extra.pendingDelete,
                pendingGroupDelete = extra.pendingGroupDelete,
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

    fun requestDeleteGroup(id: String) = extras.update { it.copy(pendingGroupDelete = id) }
    fun dismissGroupDelete() = extras.update { it.copy(pendingGroupDelete = null) }
    fun confirmGroupDelete() {
        val id = extras.value.pendingGroupDelete ?: return
        extras.update { it.copy(pendingGroupDelete = null) }
        viewModelScope.launch {
            when (val result = removeGroupUc(id, true)) {
                is UseCaseResult.Success -> Unit
                is UseCaseResult.Failure -> extras.update { it.copy(actionError = result.error) }
            }
        }
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
        val pendingGroupDelete: String? = null,
        val actionError: DownloadError? = null,
    )

    private data class HomeLoaded(
        val tasks: List<DownloadTask>,
        val groups: List<DownloadGroup>,
        val details: Map<String, TorrentTaskDetails>,
    )

    private fun DownloadGroup.toHomeGroup(tasks: List<DownloadTask>): HomeGroup {
        val downloaded = tasks.sumOf { it.downloadedBytes }
        val totals = tasks.mapNotNull { it.totalBytes }
        val total = if (tasks.isNotEmpty() && totals.size == tasks.size) totals.sum() else null
        return HomeGroup(
            group = this,
            tasks = tasks,
            downloadedBytes = downloaded,
            totalBytes = total,
            state = aggregateState(tasks),
        )
    }

    /** Deterministic aggregate state across mixed child states. */
    private fun aggregateState(tasks: List<DownloadTask>): DownloadState {
        fun any(vararg states: DownloadState) = tasks.any { it.state in states }
        return when {
            any(
                DownloadState.STARTING, DownloadState.RUNNING, DownloadState.VERIFYING,
                DownloadState.POST_PROCESSING, DownloadState.PAUSING,
            ) -> DownloadState.RUNNING
            any(DownloadState.PAUSED) -> DownloadState.PAUSED
            any(
                DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION,
                DownloadState.BLOCKED, DownloadState.NEEDS_PERMISSION,
            ) -> DownloadState.FAILED
            any(DownloadState.QUEUED, DownloadState.RETRY_WAIT) -> DownloadState.QUEUED
            tasks.isNotEmpty() && tasks.all { it.state == DownloadState.COMPLETED } -> DownloadState.COMPLETED
            else -> tasks.firstOrNull()?.state ?: DownloadState.CREATED
        }
    }

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
