package com.pulldock.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Player state (T157): a library item with its persisted resume position. */
data class PlayerUiState(
    val loading: Boolean = true,
    val item: LibraryItem? = null,
    val resumePositionMs: Long = 0L,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    library: MediaLibraryRepository,
    private val positions: PlaybackPositionStore,
) : ViewModel() {

    val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()

    val uiState: StateFlow<PlayerUiState> = combine(
        library.observe(com.pulldock.domain.download.repository.LibraryQuery()),
        if (itemId.isBlank()) kotlinx.coroutines.flow.flowOf(0L) else positions.position(itemId),
    ) { items, position ->
        PlayerUiState(
            loading = false,
            item = items.firstOrNull { it.id == itemId },
            resumePositionMs = position,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    fun savePosition(positionMs: Long) {
        if (itemId.isBlank()) return
        viewModelScope.launch { positions.save(itemId, positionMs) }
    }

    fun clearPosition() {
        if (itemId.isBlank()) return
        viewModelScope.launch { positions.clear(itemId) }
    }
}
