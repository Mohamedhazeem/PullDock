package com.pulldock.feature.downloaddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.domain.download.usecase.CancelDownload
import com.pulldock.domain.download.usecase.DownloadDetails
import com.pulldock.domain.download.usecase.ObserveDownloadDetails
import com.pulldock.domain.download.usecase.PauseDownload
import com.pulldock.domain.download.usecase.RemoveDownload
import com.pulldock.domain.download.usecase.ResumeDownload
import com.pulldock.domain.download.usecase.RetryDownload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Detail UI state (T046): observed task + engine details + action errors. */
data class DownloadDetailUiState(
    val loading: Boolean = true,
    val details: DownloadDetails? = null,
    /** Set when the task no longer exists (removed elsewhere); the screen exits. */
    val missing: Boolean = false,
    val actionError: com.pulldock.core.model.DownloadError? = null,
)

/**
 * Download detail ViewModel (T046): one task with its engine-specific details and
 * guarded per-task actions (FR-007). Every action routes through the domain use cases;
 * failures surface as typed, user-readable errors (US4 scenario: independent actions).
 */
@HiltViewModel
class DownloadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDownloadDetails: ObserveDownloadDetails,
    private val pauseUc: PauseDownload,
    private val resumeUc: ResumeDownload,
    private val cancelUc: CancelDownload,
    private val retryUc: RetryDownload,
    private val removeUc: RemoveDownload,
) : ViewModel() {

    val taskId: String = savedStateHandle.get<String>(KEY_TASK_ID).orEmpty()

    private val actionError = MutableStateFlow<com.pulldock.core.model.DownloadError?>(null)

    val uiState: StateFlow<DownloadDetailUiState> = combine(
        observeDownloadDetails(taskId),
        actionError,
    ) { details, error ->
        when (details) {
            null -> DownloadDetailUiState(loading = details != null, missing = true, actionError = error)
            else -> DownloadDetailUiState(loading = false, details = details, actionError = error)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadDetailUiState())

    fun pause(id: String): Unit = launchAction { pauseUc(id) }
    fun resume(id: String): Unit = launchAction { resumeUc(id) }
    fun retry(id: String): Unit = launchAction { retryUc(id) }
    fun cancel(id: String): Unit = launchAction { cancelUc(id) }

    /** Confirmed removal (dialog shown by the screen); file deletion stays separate. */
    fun remove(id: String, deleteFile: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            removeUc(id, deleteFile)
            onDone()
        }
    }

    fun consumeActionError() {
        actionError.update { null }
    }

    private fun launchAction(action: suspend () -> com.pulldock.domain.download.usecase.UseCaseResult<Unit>) {
        viewModelScope.launch {
            when (val result = action()) {
                is com.pulldock.domain.download.usecase.UseCaseResult.Success -> Unit
                is com.pulldock.domain.download.usecase.UseCaseResult.Failure -> actionError.update { result.error }
            }
        }
    }

    private companion object {
        const val KEY_TASK_ID = "taskId"
    }
}
