package com.pulldock.feature.discovery

import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.ProviderStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchHistoryEntry
import com.pulldock.domain.discovery.model.SortOrder
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchResult

/** Immutable UI state for Discover (unidirectional data flow). */
data class DiscoverUiState(
    val query: String = "",
    val category: SearchCategory = SearchCategory.ALL,
    val sort: SortOrder = SortOrder.RELEVANCE,
    val minSeeders: Int? = null,
    val overall: OverallSearchState = OverallSearchState.IDLE,
    val results: List<TorrentSearchResult> = emptyList(),
    val perProvider: List<ProviderStatus> = emptyList(),
    val history: List<SearchHistoryEntry> = emptyList(),
    val isLoadingMore: Boolean = false,
    val details: TorrentResultDetails? = null,
    val selectedResult: TorrentSearchResult? = null,
    val detailsLoading: Boolean = false,
    val message: String? = null,
    val hasMore: Boolean = false,
    val historyEnabled: Boolean = true,
    val tpbEnabled: Boolean = false,
    val torznabConfigs: List<ProviderConfiguration> = emptyList(),
) {
    val isSearching: Boolean get() = overall == OverallSearchState.LOADING || overall == OverallSearchState.REFRESHING
}