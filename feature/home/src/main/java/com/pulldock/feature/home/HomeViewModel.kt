package com.pulldock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.core.model.DownloadError
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.LibraryQuery
import com.pulldock.domain.download.repository.MediaLibraryRepository
import com.pulldock.domain.download.state.DownloadStateMachine
import com.pulldock.domain.download.usecase.CancelDownload
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
import kotlinx.coroutines.flow.Flow
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

/** Home dashboard state: summary, recent tasks with torrent stats, and action feedback. */
data class HomeUiState(
    val loading: Boolean = true,
    val activeCount: Int = 0,
    val queuedCount: Int = 0,
    val recent: List<DownloadTask> = emptyList(),
    val torrentDetails: Map<String, TorrentTaskDetails> = emptyMap(),
    val completed: List<LibraryItem> = emptyList(),
    val pendingRemoval: String? = null,
    val actionError: DownloadError? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDownloadQueue: ObserveDownloadQueue,
    observeDownloadDetails: ObserveDownloadDetails,
    private val pauseUc: PauseDownload,
    private val resumeUc: ResumeDownload,
    private val retryUc: RetryDownload,
    private val cancelUc: CancelDownload,
    private val removeUc: RemoveDownload,
    library: MediaLibraryRepository,
) : ViewModel() {

    private val extras = MutableStateFlow(HomeExtras())

    val uiState: StateFlow<HomeUiState> = observeDownloadQueue(QueueFilter.ALL)
        .flatMapLatest { tasks ->
            val recent = tasks.sortedByDescending { it.updatedAt.toEpochMilli() }.take(RECENT_LIMIT)
            if (recent.isEmpty()) {
                flowOf(Triple(tasks, recent, emptyMap<String, TorrentTaskDetails>()))
            } else {
                combine(
                    recent.map { task ->
                        observeDownloadDetails(task.id).map { details -> task.id to details?.torrent }
                    },
                ) { entries ->
                    Triple(
                        tasks,
                        recent,
                        entries.mapNotNull { (id, details) -> details?.let { id to it } }.toMap(),
                    )
                }
            }
        }
        .combine(extras) { (all, recent, details), extra -> Triple(all, recent, details) to extra }
        .combine(library.observe(LibraryQuery())) { (data, extra), completed ->
            val (all, recent, details) = data
            HomeUiState(
                loading = false,
                activeCount = all.count { it.state in DownloadStateMachine.activeStates },
                queuedCount = all.count {
                    it.state == com.pulldock.core.model.DownloadState.QUEUED ||
                        it.state == com.pulldock.core.model.DownloadState.RETRY_WAIT
                },
                recent = recent,
                torrentDetails = details,
                completed = completed.take(COMPLETED_LIMIT),
                pendingRemoval = extra.pendingRemoval,
                actionError = extra.actionError,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun pause(id: String) = launchAction(id) { pauseUc(id) }
    fun resume(id: String) = launchAction(id) { resumeUc(id) }
    fun retry(id: String) = launchAction(id) { retryUc(id) }
    fun cancel(id: String) = launchAction(id) { cancelUc(id) }

    fun requestRemove(id: String) = extras.update { it.copy(pendingRemoval = id) }
    fun dismissRemove() = extras.update { it.copy(pendingRemoval = null) }
    fun confirmRemove(deleteFile: Boolean) {
        val id = extras.value.pendingRemoval ?: return
        extras.update { it.copy(pendingRemoval = null) }
        launchAction(id) { removeUc(id, deleteFile) }
    }
    fun consumeActionError() = extras.update { it.copy(actionError = null) }

    private fun launchAction(id: String, action: suspend () -> UseCaseResult<Unit>) {
        viewModelScope.launch {
            when (val result = action()) {
                is UseCaseResult.Success -> Unit
                is UseCaseResult.Failure -> extras.update { it.copy(actionError = result.error) }
            }
        }
    }

    private data class HomeExtras(
        val pendingRemoval: String? = null,
        val actionError: DownloadError? = null,
    )

    private companion object {
        const val RECENT_LIMIT = 5
        const val COMPLETED_LIMIT = 3
    }
}
