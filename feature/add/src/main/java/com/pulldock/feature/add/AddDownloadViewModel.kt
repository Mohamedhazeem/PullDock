package com.pulldock.feature.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.core.model.DownloadError
import com.pulldock.domain.download.model.StorageDestinationDefaults
import com.pulldock.domain.download.usecase.AddTorrent
import com.pulldock.domain.download.usecase.EnqueueDownload
import com.pulldock.domain.download.usecase.FetchAvailableFormats
import com.pulldock.domain.download.usecase.ResolveMediaUrl
import com.pulldock.domain.download.usecase.UseCaseResult
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.torrent.model.TorrentSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The detected source kind for the entry field. */
enum class SourceKind { NONE, MAGNET, MEDIA_URL, UNSUPPORTED }

/** Add-download UI state (T044/T087): entry, detection, validation, format selection. */
data class AddDownloadUiState(
    val input: String = "",
    val sourceKind: SourceKind = SourceKind.NONE,
    val submitting: Boolean = false,
    /** Non-null when the current input failed typed validation (inline field error). */
    val inputError: DownloadError? = null,
    /** Resolved media source awaiting format selection (US3 scenarios 1-2). */
    val resolvedDescriptor: MediaDescriptor? = null,
    /** Available formats for the resolved descriptor; typed-empty renders an error. */
    val formats: List<MediaFormat> = emptyList(),
    val selectedFormat: MediaFormat? = null,
    /** Flow-level failure (network/runtime) rendered as a full error state. */
    val failedError: DownloadError? = null,
)

/** One-shot add-flow events. */
sealed interface AddDownloadEvent {
    /** Torrent task created and queued. */
    data object TorrentAdded : AddDownloadEvent

    /** Media task created and queued for the chosen format. */
    data object MediaEnqueued : AddDownloadEvent
}

/**
 * Add-download flow (FR-001/FR-002, US2/US3 entry). Classifies the input, validates
 * typed, enqueues torrent tasks, and drives media resolution, format selection, and
 * enqueue. No fake success: failures surface typed; media tasks only exist after a
 * real descriptor and an explicit format choice.
 */
@HiltViewModel
class AddDownloadViewModel @Inject constructor(
    private val addTorrent: AddTorrent,
    private val resolveMediaUrl: ResolveMediaUrl,
    private val fetchAvailableFormats: FetchAvailableFormats,
    private val enqueueDownload: EnqueueDownload,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddDownloadUiState())
    val uiState: StateFlow<AddDownloadUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddDownloadEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AddDownloadEvent> = _events.asSharedFlow()

    /** Updates the entry and re-classifies; errors clear on edit. */
    fun onInputChanged(value: String) {
        _uiState.update {
            it.copy(input = value, sourceKind = classify(value), inputError = null, failedError = null)
        }
    }

    /** Submits the current input; duplicate/malformed sources surface typed errors. */
    fun submit() {
        val state = _uiState.value
        if (state.submitting) return
        val trimmed = state.input.trim()
        when (classify(trimmed)) {
            SourceKind.NONE, SourceKind.UNSUPPORTED -> _uiState.update {
                it.copy(inputError = DownloadError.Input(DownloadError.InputReason.UNSUPPORTED_SCHEME))
            }
            SourceKind.MAGNET -> submitTorrent(trimmed)
            SourceKind.MEDIA_URL -> submitMedia(trimmed)
        }
    }

    /** Selects a format (remembered for the download, US3 scenario 2). */
    fun selectFormat(format: MediaFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    /** Enqueues the media download for the chosen format; duplicates fail typed. */
    fun enqueueMedia() {
        val descriptor = _uiState.value.resolvedDescriptor ?: return
        val format = _uiState.value.selectedFormat ?: return
        _uiState.update { it.copy(submitting = true, failedError = null) }
        viewModelScope.launch {
            try {
                when (val result = enqueueDownload(descriptor, format, StorageDestinationDefaults.appSpecific())) {
                    is UseCaseResult.Success -> {
                        _uiState.update { AddDownloadUiState() }
                        _events.tryEmit(AddDownloadEvent.MediaEnqueued)
                    }
                    is UseCaseResult.Failure -> _uiState.update { it.copy(inputError = result.error) }
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    /** Submits a `.torrent` document picked via SAF (T065, FR-001). */
    fun onTorrentFilePicked(uri: String) {
        if (_uiState.value.submitting) return
        _uiState.update { it.copy(submitting = true, inputError = null, failedError = null) }
        viewModelScope.launch {
            try {
                when (val result = addTorrent(TorrentSource(TorrentSource.Kind.TORRENT_URI, uri), StorageDestinationDefaults.appSpecific())) {
                    is UseCaseResult.Success -> _events.tryEmit(AddDownloadEvent.TorrentAdded)
                    is UseCaseResult.Failure -> _uiState.update { it.copy(inputError = result.error) }
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    /** Dismisses the resolved-media sheet; the user can edit the URL instead. */
    fun dismissResolved() {
        _uiState.update { it.copy(resolvedDescriptor = null, formats = emptyList(), selectedFormat = null) }
    }

    private fun submitTorrent(magnet: String) {
        _uiState.update { it.copy(submitting = true, inputError = null, failedError = null) }
        viewModelScope.launch {
            try {
                when (val result = addTorrent(TorrentSource(TorrentSource.Kind.MAGNET, magnet), StorageDestinationDefaults.appSpecific())) {
                    is UseCaseResult.Success -> _events.tryEmit(AddDownloadEvent.TorrentAdded)
                    is UseCaseResult.Failure -> _uiState.update { it.copy(inputError = result.error) }
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    private fun submitMedia(url: String) {
        _uiState.update { it.copy(submitting = true, inputError = null, failedError = null) }
        viewModelScope.launch {
            try {
                when (val result = resolveMediaUrl(url)) {
                    is ExtractorResult.Success -> loadFormats(result.value)
                    is ExtractorResult.Failure -> _uiState.update { it.copy(failedError = result.error) }
                }
            } finally {
                _uiState.update { it.copy(submitting = false) }
            }
        }
    }

    /** Loads formats for the resolved descriptor; typed-empty is surfaced, not faked. */
    private suspend fun loadFormats(descriptor: MediaDescriptor) {
        when (val formatsResult = fetchAvailableFormats(descriptor)) {
            is ExtractorResult.Success ->
                _uiState.update { it.copy(resolvedDescriptor = descriptor, formats = formatsResult.value, selectedFormat = null) }
            is ExtractorResult.Failure -> _uiState.update { it.copy(failedError = formatsResult.error) }
        }
    }

    /** Input classification: magnet scheme beats URL checks; anything else unsupported. */
    private fun classify(value: String): SourceKind {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("magnet:", ignoreCase = true) -> SourceKind.MAGNET
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> SourceKind.MEDIA_URL
            trimmed.isBlank() -> SourceKind.NONE
            else -> SourceKind.UNSUPPORTED
        }
    }
}
