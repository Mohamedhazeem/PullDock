package com.pulldock.feature.discovery

import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SortOrder
import com.pulldock.domain.discovery.model.TorrentSearchResult

/** User intents for the Discover screen (state down, events up). */
sealed interface DiscoverUiEvent {
    data class QueryChanged(val value: String) : DiscoverUiEvent
    data class CategorySelected(val category: SearchCategory) : DiscoverUiEvent
    data class SortSelected(val sort: SortOrder) : DiscoverUiEvent
    data class MinSeedersChanged(val value: Int?) : DiscoverUiEvent
    data object Submit : DiscoverUiEvent
    data object LoadMore : DiscoverUiEvent
    data object Refresh : DiscoverUiEvent
    data class OpenDetails(val result: TorrentSearchResult) : DiscoverUiEvent
    data object CloseDetails : DiscoverUiEvent
    data class AddToPullDock(val result: TorrentSearchResult, val fileName: String? = null) : DiscoverUiEvent
    data class RepeatSearch(val query: String) : DiscoverUiEvent
    data class DeleteHistory(val query: String) : DiscoverUiEvent
    data object ClearHistory : DiscoverUiEvent
    data class SetHistoryEnabled(val enabled: Boolean) : DiscoverUiEvent
    data object ClearCache : DiscoverUiEvent
    data class HideResult(val resultId: String) : DiscoverUiEvent
    data class ReportResult(val resultId: String) : DiscoverUiEvent
    data class AddTorznab(val config: ProviderConfiguration, val apiKey: String?) : DiscoverUiEvent
    data class RemoveTorznab(val providerId: String) : DiscoverUiEvent
    data object RestoreTorznabDefaults : DiscoverUiEvent
    data object ToggleTpb : DiscoverUiEvent
    data class SetApiKey(val providerId: String, val key: String?) : DiscoverUiEvent
    data class ProviderSelectionChanged(val providerIds: Set<String>) : DiscoverUiEvent
    data class SizeRangeChanged(val minMb: Int?, val maxMb: Int?) : DiscoverUiEvent
    data class DateRangeChanged(val from: String?, val to: String?) : DiscoverUiEvent
    data class FiltersApplied(
        val sort: SortOrder,
        val minSeeders: Int?,
        val providerIds: Set<String>,
        val minMb: Int?,
        val maxMb: Int?,
        val from: String?,
        val to: String?,
    ) : DiscoverUiEvent
}