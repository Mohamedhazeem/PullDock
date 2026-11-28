package com.pulldock.feature.discovery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.feature.discovery.components.ProviderStatusRow
import com.pulldock.feature.discovery.components.ResultCard
import com.pulldock.feature.discovery.components.icon
import com.pulldock.feature.discovery.details.ResultDetailsScreen
import com.pulldock.feature.discovery.filters.FilterSheet
import com.pulldock.feature.discovery.history.SearchHistoryRow
import com.pulldock.feature.discovery.settings.DiscoverySettingsScreen

/** Route wrapper: obtains the ViewModel and forwards one-shot Add-to-PullDock sources. */
@Composable
fun DiscoverRoute(
    onAddSource: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.addSource.collect { onAddSource(it) } }
    val state by viewModel.state.collectAsState()
    if (state.details != null || state.detailsLoading) {
        ResultDetailsScreen(
            details = state.details,
            loading = state.detailsLoading,
            onBack = { viewModel.onEvent(DiscoverUiEvent.CloseDetails) },
            onAdd = { fileName -> state.selectedResult?.let { viewModel.onEvent(DiscoverUiEvent.AddToPullDock(it, fileName)) } },
        )
    } else {
        DiscoverScreen(state = state, onEvent = viewModel::onEvent, onOpenSettings = onOpenSettings, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onEvent: (DiscoverUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Discover") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Discovery settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = tokens.spacing.md)) {
            SearchBar(state.query, onEvent)
            Spacer(Modifier.height(tokens.spacing.sm))
            CategoryChips(state.category, state.adultEnabled) { onEvent(DiscoverUiEvent.CategorySelected(it)) }
            TextButton(onClick = { showFilters = true }) { Text("Filters") }
            if (state.perProvider.isNotEmpty()) ProviderStatusRow(state.perProvider)
            if (state.history.isNotEmpty()) {
                SearchHistoryRow(state.history, onRepeat = { onEvent(DiscoverUiEvent.RepeatSearch(it)) }, onClear = { onEvent(DiscoverUiEvent.ClearHistory) })
            }
            Spacer(Modifier.height(tokens.spacing.sm))
            Box(Modifier.fillMaxSize()) {
                when (state.overall) {
                    OverallSearchState.LOADING, OverallSearchState.REFRESHING ->
                        CenterState { CircularProgressIndicator(); Text("Searching…", style = MaterialTheme.typography.bodyMedium) }
                    OverallSearchState.EMPTY ->
                        CenterState(icon = { Icon(Icons.Rounded.Inbox, null, Modifier.size(40.dp)) }, title = "No results", body = "Try a different title or category.")
                    OverallSearchState.ALL_FAILED ->
                        CenterState(icon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(40.dp)) }, title = "All providers failed", body = "Check connectivity and retry.", action = { TextButton(onClick = { onEvent(DiscoverUiEvent.Refresh) }) { Text("Retry") } })
                    OverallSearchState.OFFLINE ->
                        CenterState(icon = { Icon(Icons.Rounded.CloudOff, null, Modifier.size(40.dp)) }, title = "You're offline", body = "Reconnect and retry.")
                    else -> ResultsList(state, onEvent)
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(
            currentSort = state.sort,
            currentMinSeeders = state.minSeeders,
            providers = state.perProvider,
            currentProviderIds = emptySet(),
            onApply = { sort, min, provs, minMb, maxMb, from, to ->
                onEvent(DiscoverUiEvent.SortSelected(sort)); onEvent(DiscoverUiEvent.MinSeedersChanged(min))
                onEvent(DiscoverUiEvent.ProviderSelectionChanged(provs)); onEvent(DiscoverUiEvent.SizeRangeChanged(minMb, maxMb))
                onEvent(DiscoverUiEvent.DateRangeChanged(from, to)); showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun SearchBar(query: String, onEvent: (DiscoverUiEvent) -> Unit) {
    val tokens = LocalPdTokens.current
    OutlinedTextField(
        value = query,
        onValueChange = { onEvent(DiscoverUiEvent.QueryChanged(it)) },
        placeholder = { Text("Search movies, shows, books…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { onEvent(DiscoverUiEvent.Submit) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(tokens.radius.lg),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
        TextButton(onClick = { onEvent(DiscoverUiEvent.Submit) }) { Text("Search") }
        TextButton(onClick = { onEvent(DiscoverUiEvent.Refresh) }) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Text(" Refresh") }
        TextButton(onClick = { onEvent(DiscoverUiEvent.ClearHistory) }) { Text("Clear history") }
    }
}

@Composable
private fun CategoryChips(selected: SearchCategory, adultEnabled: Boolean, onSelect: (SearchCategory) -> Unit) {
    val tokens = LocalPdTokens.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        SearchCategory.entries.filter { it != SearchCategory.ADULT || adultEnabled }.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.label()) },
                leadingIcon = if (selected == category) ({ Icon(category.icon(), null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}

@Composable
private fun ResultsList(state: DiscoverUiState, onEvent: (DiscoverUiEvent) -> Unit) {
    val tokens = LocalPdTokens.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = tokens.spacing.sm)) {
        items(state.results, key = { it.resultId }) { result ->
            ResultCard(
                result = result,
                onOpen = { onEvent(DiscoverUiEvent.OpenDetails(result)) },
                onAdd = { onEvent(DiscoverUiEvent.AddToPullDock(result)) },
                onHide = { onEvent(DiscoverUiEvent.HideResult(result.resultId)) },
                onReport = { onEvent(DiscoverUiEvent.ReportResult(result.resultId)) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (state.hasMore) TextButton(onClick = { onEvent(DiscoverUiEvent.LoadMore) }) { Text("Load more") }
            }
        }
    }
}

@Composable
private fun CenterState(
    icon: @Composable () -> Unit = {},
    title: String? = null,
    body: String? = null,
    action: @Composable () -> Unit = {},
) {
    val tokens = LocalPdTokens.current
    Column(
        Modifier.fillMaxSize().padding(tokens.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

private fun SearchCategory.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }