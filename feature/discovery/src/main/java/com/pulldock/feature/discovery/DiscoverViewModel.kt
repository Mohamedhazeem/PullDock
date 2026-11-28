package com.pulldock.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.DiscoveryCache
import com.pulldock.domain.discovery.SearchHistoryRepository
import com.pulldock.domain.discovery.TorrentSearchRepository
import com.pulldock.domain.discovery.TorznabConfigRepository
import com.pulldock.domain.discovery.model.DescriptorKind
import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.ProviderState
import com.pulldock.domain.discovery.model.ProviderStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchFilters
import com.pulldock.domain.discovery.model.SearchHistoryEntry
import com.pulldock.domain.discovery.model.SortOrder
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Discover ViewModel: owns search state and cancellation, emits a one-shot
 * `AddToPullDock(source)` event after resolving a validated download descriptor, and never
 * touches the engine, Room, or storage directly (FR-004).
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repository: TorrentSearchRepository,
    private val historyStore: SearchHistoryRepository,
    private val searchResultCache: DiscoveryCache,
    private val torznabConfig: TorznabConfigRepository,
    private val credentials: ApiCredentialStore,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _addSource = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val addSource: SharedFlow<String> = _addSource.asSharedFlow()

    private var searchJob: Job? = null
    private var lastQuery: TorrentSearchQuery? = null
    private val hiddenIds = mutableSetOf<String>()
    private var adultEnabled = false

    // Filter state not yet applied upstream (client-side until submit).
    private var providerIds: Set<String> = emptySet()
    private var minSizeBytes: Long? = null
    private var maxSizeBytes: Long? = null
    private var dateFrom: Instant? = null
    private var dateTo: Instant? = null

    init {
        historyStore.observe().collectSafely { history -> _state.update { it.copy(history = history) } }
        historyStore.enabled.collectSafely { enabled -> _state.update { it.copy(historyEnabled = enabled) } }
        torznabConfig.observe().collectSafely { configs -> _state.update { it.copy(torznabConfigs = configs) } }
    }

    fun onEvent(event: DiscoverUiEvent) {
        when (event) {
            is DiscoverUiEvent.QueryChanged -> _state.update { it.copy(query = event.value) }
            is DiscoverUiEvent.CategorySelected -> {
                _state.update { it.copy(category = event.category) }
                if (lastQuery != null) submit()
            }
            is DiscoverUiEvent.SortSelected -> { _state.update { it.copy(sort = event.sort) }; if (lastQuery != null) submit() }
            is DiscoverUiEvent.MinSeedersChanged -> { _state.update { it.copy(minSeeders = event.value) }; if (lastQuery != null) submit() }
            DiscoverUiEvent.Submit -> submit()
            DiscoverUiEvent.LoadMore -> loadMore()
            DiscoverUiEvent.Refresh -> refresh()
            is DiscoverUiEvent.OpenDetails -> openDetails(event.result)
            DiscoverUiEvent.CloseDetails -> _state.update { it.copy(details = null, selectedResult = null, detailsLoading = false) }
            is DiscoverUiEvent.AddToPullDock -> addToPullDock(event.result, event.fileName)
            is DiscoverUiEvent.RepeatSearch -> { _state.update { it.copy(query = event.query) }; submit() }
            is DiscoverUiEvent.DeleteHistory -> viewModelScope.launch { historyStore.delete(event.query.toEntry()) }
            DiscoverUiEvent.ClearHistory -> viewModelScope.launch { historyStore.clear() }
            DiscoverUiEvent.ToggleAdult -> { adultEnabled = !adultEnabled; _state.update { it.copy(adultEnabled = adultEnabled) } }
            is DiscoverUiEvent.SetHistoryEnabled -> viewModelScope.launch {
                historyStore.setEnabled(event.enabled)
                _state.update { it.copy(historyEnabled = event.enabled) }
            }
            DiscoverUiEvent.ClearCache -> searchResultCache.clear()
            is DiscoverUiEvent.HideResult -> { hiddenIds += event.resultId; _state.update { it.copy(results = it.results.filterNot { r -> r.resultId in hiddenIds }) } }
            is DiscoverUiEvent.ReportResult -> {
                hiddenIds += event.resultId
                _state.update { it.copy(results = it.results.filterNot { r -> r.resultId in hiddenIds }, message = "Reported. Hidden from results.") }
            }
            is DiscoverUiEvent.AddTorznab -> viewModelScope.launch {
                credentials.set(event.config.providerId, event.apiKey)
                torznabConfig.replace(_state.value.torznabConfigs + event.config)
            }
            is DiscoverUiEvent.RemoveTorznab -> viewModelScope.launch {
                credentials.set(event.providerId, null)
                torznabConfig.replace(_state.value.torznabConfigs.filterNot { it.providerId == event.providerId })
            }
            DiscoverUiEvent.RestoreTorznabDefaults -> viewModelScope.launch { torznabConfig.restoreDefaults() }
            is DiscoverUiEvent.SetApiKey -> credentials.set(event.providerId, event.key)
            is DiscoverUiEvent.ProviderSelectionChanged -> { providerIds = event.providerIds; if (lastQuery != null) submit() }
            is DiscoverUiEvent.SizeRangeChanged -> {
                minSizeBytes = event.minMb?.toLong()?.times(1_000_000L)
                maxSizeBytes = event.maxMb?.toLong()?.times(1_000_000L)
                if (lastQuery != null) submit()
            }
            is DiscoverUiEvent.DateRangeChanged -> {
                dateFrom = event.from?.let(::parseDate)
                dateTo = event.to?.let(::parseDate)
                if (lastQuery != null) submit()
            }
            is DiscoverUiEvent.FiltersApplied -> {
                _state.update { it.copy(sort = event.sort, minSeeders = event.minSeeders) }
                providerIds = event.providerIds
                minSizeBytes = event.minMb?.toLong()?.times(1_000_000L)
                maxSizeBytes = event.maxMb?.toLong()?.times(1_000_000L)
                dateFrom = event.from?.let(::parseDate)
                dateTo = event.to?.let(::parseDate)
                if (lastQuery != null) submit()
            }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.query.isBlank()) {
            _state.update { it.copy(overall = OverallSearchState.EMPTY, results = emptyList(), perProvider = emptyList()) }
            return
        }
        searchJob?.cancel()
        _state.update { it.copy(overall = OverallSearchState.LOADING, details = null, hasMore = false) }
        searchJob = viewModelScope.launch(dispatchers.io) {
            val query = current.toQuery()
            lastQuery = query
            val page = repository.search(query) { status, progressive ->
                _state.update { state ->
                    val merged = (state.perProvider.filterNot { it.providerId == status.providerId } + status)
                    state.copy(results = visible(progressive), perProvider = merged)
                }
            }
            _state.update { it.copy(overall = page.overall, results = visible(page.results), perProvider = page.perProvider, hasMore = page.hasMoreByProvider.values.any { it }) }
            historyStore.add(query.text, query.filters.category)
        }
    }

    private fun loadMore() {
        val query = lastQuery ?: return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch(dispatchers.io) {
            val page = repository.loadMore(query)
            _state.update { it.copy(overall = page.overall, results = visible(page.results), perProvider = page.perProvider, isLoadingMore = false, hasMore = page.hasMoreByProvider.values.any { it }) }
        }
    }

    private fun refresh() {
        val query = lastQuery ?: return
        _state.update { it.copy(overall = OverallSearchState.REFRESHING) }
        viewModelScope.launch(dispatchers.io) {
            val page = repository.refresh(query)
            _state.update { it.copy(overall = page.overall, results = visible(page.results), perProvider = page.perProvider, hasMore = page.hasMoreByProvider.values.any { it }) }
        }
    }

    private fun openDetails(result: TorrentSearchResult) {
        _state.update { it.copy(detailsLoading = true, details = null, selectedResult = result) }
        viewModelScope.launch(dispatchers.io) {
            val details = repository.getDetails(result)
            _state.update { it.copy(details = details, detailsLoading = false) }
        }
    }

    private fun addToPullDock(result: TorrentSearchResult, fileName: String?) {
        viewModelScope.launch(dispatchers.io) {
            val descriptor = try {
                repository.resolveDownloadDescriptor(repository.getDetails(result), fileName)
            } catch (e: Exception) {
                null
            }
            val source = when (descriptor?.kind) {
                DescriptorKind.MAGNET -> descriptor.magnetUri
                DescriptorKind.TORRENT_FILE -> descriptor.torrentFileRef
                null -> {
                    // Fallback: a search result that already carries a provider infohash can
                    // still enter the Add flow as a minimal magnet (xt only) when the detail
                    // endpoint is unavailable. The hash came from the provider, not fabricated.
                    if (result.resultSourceType == com.pulldock.domain.discovery.model.ResultSourceType.MAGNET &&
                        !result.infoHash.isNullOrBlank()
                    ) {
                        "magnet:?xt=urn:btih:${result.infoHash}"
                    } else {
                        null
                    }
                }
            }
            if (source != null) {
                _addSource.emit(source)
            } else {
                _state.update { it.copy(message = "No usable torrent for this result.") }
            }
        }
    }

    private fun visible(results: List<TorrentSearchResult>): List<TorrentSearchResult> =
        results.filterNot { it.resultId in hiddenIds || (!adultEnabled && isAdult(it)) }

    private fun isAdult(result: TorrentSearchResult): Boolean =
        result.category == SearchCategory.ADULT ||
            ADULT_MARKERS.any { result.title.contains(it, ignoreCase = true) }

    private companion object {
        val ADULT_MARKERS = listOf(
            "xxx", "porn", "brazzers", "naughty", "erotic", "hentai", "milf",
            "creampie", "onlyfans", "nsfw", "adult video", "wank", "bangbros",
            "mofos", "team skeet", "digital playground", "rabbit pro",
        )
    }

    private fun DiscoverUiState.toQuery(): TorrentSearchQuery = TorrentSearchQuery(
        text = query.trim(),
        filters = SearchFilters(
            category = category,
            providerIds = providerIds,
            minSeeders = minSeeders,
            minSizeBytes = minSizeBytes,
            maxSizeBytes = maxSizeBytes,
            dateFrom = dateFrom,
            dateTo = dateTo,
            sort = sort,
        ),
    )

    private fun String.toEntry() = SearchHistoryEntry(this, null, Instant.now())

    private fun parseDate(raw: String): Instant? = runCatching { Instant.parse(raw) }
        .recoverCatching { LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant() }
        .getOrNull()

    private fun <T> kotlinx.coroutines.flow.Flow<T>.collectSafely(action: suspend (T) -> Unit) {
        viewModelScope.launch(dispatchers.io) { this@collectSafely.collect { action(it) } }
    }
}