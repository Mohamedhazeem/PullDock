package com.pulldock.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.download.state.DownloadStateMachine
import com.pulldock.domain.download.usecase.ObserveDownloadQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Home dashboard state: active summary plus the most recent tasks (FR-066). */
data class HomeUiState(
    val loading: Boolean = true,
    val activeCount: Int = 0,
    val queuedCount: Int = 0,
    val recent: List<DownloadTask> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDownloadQueue: ObserveDownloadQueue,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = observeDownloadQueue(QueueFilter.ALL)
        .combine(observeDownloadQueue(QueueFilter.QUEUED)) { all, queuedAndActive ->
            HomeUiState(
                loading = false,
                activeCount = all.count { it.state in DownloadStateMachine.activeStates },
                queuedCount = all.count { it.state == DownloadState.QUEUED || it.state == DownloadState.RETRY_WAIT },
                recent = all.sortedByDescending { it.updatedAt.toEpochMilli() }.take(RECENT_LIMIT),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
