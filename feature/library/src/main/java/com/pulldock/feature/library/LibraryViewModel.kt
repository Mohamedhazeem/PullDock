package com.pulldock.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.LibraryQuery
import com.pulldock.domain.download.repository.LibrarySection
import com.pulldock.domain.download.repository.LibrarySort
import com.pulldock.domain.download.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Library state (T156, US5): query, items, reconciliation, and deletion confirmation. */
data class LibraryUiState(
    val loading: Boolean = true,
    val query: LibraryQuery = LibraryQuery(),
    val items: List<LibraryItem> = emptyList(),
    val pendingDelete: String? = null,
    val lastReconciled: Int? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: MediaLibraryRepository,
) : ViewModel() {

    private val query = MutableStateFlow(LibraryQuery())
    private val pendingDelete = MutableStateFlow<String?>(null)
    private val reconciledCount = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<LibraryUiState> = query
        .flatMapLatest { library.observe(it) }
        .combine(pendingDelete) { items, deleteId -> items to deleteId }
        .combine(reconciledCount) { (items, deleteId), count ->
            LibraryUiState(
                loading = false,
                query = query.value,
                items = items,
                pendingDelete = deleteId,
                lastReconciled = count,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setSection(section: LibrarySection) = query.update { it.copy(section = section) }
    fun setSearch(text: String) = query.update { it.copy(searchText = text.ifBlank { null }) }
    fun setSort(sort: LibrarySort) = query.update { it.copy(sortBy = sort) }

    /** FR-043: re-check files and mark missing entries unavailable, never delete silently. */
    fun reconcile() = viewModelScope.launch {
        reconciledCount.value = library.reconcile().missing
    }

    fun requestDelete(id: String) = pendingDelete.update { id }
    fun dismissDelete() = pendingDelete.update { null }
    fun confirmDelete(deleteFile: Boolean) {
        val id = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { library.delete(id, deleteFile) }
    }
}
