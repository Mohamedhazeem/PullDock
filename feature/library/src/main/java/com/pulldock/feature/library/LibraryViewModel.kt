package com.pulldock.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.domain.download.model.DownloadGroup
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.LibraryQuery
import com.pulldock.domain.download.repository.LibrarySection
import com.pulldock.domain.download.repository.LibrarySort
import com.pulldock.domain.download.repository.MediaLibraryRepository
import com.pulldock.domain.download.usecase.ObserveGroups
import com.pulldock.domain.download.usecase.RemoveGroup
import com.pulldock.domain.download.usecase.UseCaseResult
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

/** A group of library items shown as one expandable card (playlist / multi-file torrent). */
data class LibraryGroup(
    val group: DownloadGroup,
    val items: List<LibraryItem>,
    val totalBytes: Long,
)

/** Library state (T156, US5): query, items, reconciliation, and deletion confirmation. */
data class LibraryUiState(
    val loading: Boolean = true,
    val query: LibraryQuery = LibraryQuery(),
    /** Ungrouped items (standalone downloads). */
    val items: List<LibraryItem> = emptyList(),
    /** Grouped items (playlists, multi-file torrents). */
    val groups: List<LibraryGroup> = emptyList(),
    val pendingDelete: String? = null,
    val pendingGroupDelete: String? = null,
    val lastReconciled: Int? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty() && groups.isEmpty()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: MediaLibraryRepository,
    observeGroups: ObserveGroups,
    private val removeGroup: RemoveGroup,
) : ViewModel() {

    private val query = MutableStateFlow(LibraryQuery())
    private val pendingDelete = MutableStateFlow<String?>(null)
    private val pendingGroupDelete = MutableStateFlow<String?>(null)
    private val reconciledCount = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<LibraryUiState> = query
        .flatMapLatest { library.observe(it) }
        .combine(observeGroups()) { items, groups -> items to groups }
        .combine(pendingDelete) { (items, groups), deleteId -> LibraryLoaded(items, groups, deleteId, null) }
        .combine(pendingGroupDelete) { loaded, groupId -> loaded.copy(groupDelete = groupId) }
        .combine(reconciledCount) { loaded, count ->
            val groupedIds = loaded.groups.map { it.id }.toSet()
            val ungrouped = loaded.items.filter { it.groupId == null || it.groupId !in groupedIds }
            val groups = loaded.groups.mapNotNull { group ->
                val children = loaded.items.filter { it.groupId == group.id }
                if (children.isEmpty()) null else LibraryGroup(
                    group = group,
                    items = children,
                    totalBytes = children.sumOf { it.sizeBytes ?: 0L },
                )
            }
            LibraryUiState(
                loading = false,
                query = query.value,
                items = ungrouped,
                groups = groups,
                pendingDelete = loaded.pendingDelete,
                pendingGroupDelete = loaded.groupDelete,
                lastReconciled = count,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private data class LibraryLoaded(
        val items: List<LibraryItem>,
        val groups: List<DownloadGroup>,
        val pendingDelete: String?,
        val groupDelete: String?,
    )

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

    fun requestDeleteGroup(id: String) = pendingGroupDelete.update { id }
    fun dismissGroupDelete() = pendingGroupDelete.update { null }
    fun confirmGroupDelete(deleteFile: Boolean) {
        val id = pendingGroupDelete.value ?: return
        pendingGroupDelete.value = null
        viewModelScope.launch { removeGroup(id, deleteFile) }
    }
}
