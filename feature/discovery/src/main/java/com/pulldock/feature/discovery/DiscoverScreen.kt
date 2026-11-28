package com.pulldock.feature.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.feature.discovery.components.ProviderStatusRow
import com.pulldock.feature.discovery.components.ResultCard
import com.pulldock.feature.discovery.details.ResultDetailsScreen
import com.pulldock.feature.discovery.filters.FilterSheet
import com.pulldock.feature.discovery.history.SearchHistoryRow
import com.pulldock.feature.discovery.settings.DiscoverySettingsScreen
import kotlinx.coroutines.launch

/** Route wrapper: obtains the ViewModel, forwards Add sources, and closes details on back. */
@Composable
fun DiscoverRoute(
    onAddSource: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.addSource.collect { onAddSource(it) } }
    val state by viewModel.state.collectAsState()
    val detailsOpen = state.details != null || state.detailsLoading || state.detailsError
    BackHandler(enabled = detailsOpen) { viewModel.onEvent(DiscoverUiEvent.CloseDetails) }
    if (detailsOpen) {
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.overall) { if (state.overall == OverallSearchState.LOADING) listState.scrollToItem(0) }

    Scaffold(modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(state.query, onEvent, { showFilters = true }, Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Discovery settings") }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (state.overall) {
                    OverallSearchState.LOADING, OverallSearchState.REFRESHING ->
                        CenterState { CircularProgressIndicator(); Text("Searching…", style = MaterialTheme.typography.bodyMedium) }
                    OverallSearchState.EMPTY ->
                        CenterState(icon = { Icon(Icons.Rounded.Inbox, null, Modifier.size(40.dp)) }, title = "No results", body = "Try a different title or category.")
                    OverallSearchState.ALL_FAILED ->
                        CenterState(icon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(40.dp)) }, title = "All providers failed", body = "Check connectivity and retry.", action = { TextButton(onClick = { onEvent(DiscoverUiEvent.Refresh) }) { Text("Retry") } })
                    OverallSearchState.OFFLINE ->
                        CenterState(icon = { Icon(Icons.Rounded.CloudOff, null, Modifier.size(40.dp)) }, title = "You're offline", body = "Reconnect and retry.")
                    else -> LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = tokens.spacing.md, end = tokens.spacing.md, top = tokens.spacing.xs, bottom = 96.dp),
                    ) {
                        item { CategoryChips(state.category) { onEvent(DiscoverUiEvent.CategorySelected(it)) } }
                        if (state.perProvider.isNotEmpty()) item {
                            ProviderStatusRow(state.perProvider, state.selectedProviderIds, onToggle = { onEvent(DiscoverUiEvent.ToggleProvider(it)) })
                        }
                        if (state.history.isNotEmpty()) item {
                            SearchHistoryRow(state.history, onRepeat = { onEvent(DiscoverUiEvent.RepeatSearch(it)) }, onClear = { onEvent(DiscoverUiEvent.ClearHistory) })
                        }
                        if (state.results.isNotEmpty()) item {
                            Text("${state.results.size} results", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(state.results, key = { it.resultId }) { result ->
                            ResultCard(result, onOpen = { onEvent(DiscoverUiEvent.OpenDetails(result)) }, onAdd = { onEvent(DiscoverUiEvent.AddToPullDock(result)) })
                        }
                        if (state.hasMore) item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = { onEvent(DiscoverUiEvent.LoadMore) }) { Text("Load more") }
                            }
                        }
                    }
                }

                val total = listState.layoutInfo.totalItemsCount
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                if (total > 0 && last == total - 1 && listState.firstVisibleItemIndex > 0 && (state.overall == OverallSearchState.RESULTS || state.overall == OverallSearchState.PARTIAL)) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Scroll to top") }
                }
            }
        }
    }
    if (showFilters) {
        FilterSheet(
            currentSort = state.sort,
            currentMinSeeders = state.minSeeders,
            providers = state.perProvider,
            currentProviderIds = state.selectedProviderIds,
            onApply = { sort, min, provs, minMb, maxMb, from, to ->
                onEvent(DiscoverUiEvent.FiltersApplied(sort, min, provs, minMb, maxMb, from, to)); showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun SearchBar(query: String, onEvent: (DiscoverUiEvent) -> Unit, onFilters: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalPdTokens.current
    OutlinedTextField(
        value = query,
        onValueChange = { onEvent(DiscoverUiEvent.QueryChanged(it)) },
        placeholder = { Text("Search torrents…") },
        trailingIcon = {
            Row {
                IconButton(onClick = onFilters, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Tune, contentDescription = "Filters") }
                IconButton(onClick = { onEvent(DiscoverUiEvent.Submit) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) }
            }
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onEvent(DiscoverUiEvent.Submit) }),
        shape = RoundedCornerShape(tokens.radius.lg),
        modifier = modifier.height(52.dp),
    )
}

@Composable
private fun CategoryChips(selected: SearchCategory, onSelect: (SearchCategory) -> Unit) {
    val tokens = LocalPdTokens.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        SearchCategory.entries.forEach { category ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(category.label()) })
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
    Column(Modifier.fillMaxSize().padding(tokens.spacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        icon()
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

private fun SearchCategory.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }