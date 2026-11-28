package com.pulldock.feature.discovery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
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
    val detailsOpen = state.details != null || state.detailsLoading
    BackHandler(enabled = detailsOpen) { viewModel.onEvent(DiscoverUiEvent.CloseDetails) }
    if (detailsOpen) {
        ResultDetailsScreen(
            details = state.details,
            loading = state.detailsLoading,
            onBack = { viewModel.onEvent(DiscoverUiEvent.CloseDetails) },
            onAdd = { fileName -> state.selectedResult?.let { viewModel.onEvent(DiscoverUiEvent.AddToPullDock(it, fileName)) } },
            adultEnabled = state.adultEnabled,
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
    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    val total = listState.layoutInfo.totalItemsCount
    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
    val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    val atBottom = total > 0 && lastVisible == total - 1 && scrolled

    Scaffold(modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(state.query, onEvent, { showFilters = true }, Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Discovery settings") }
            }
            Column(Modifier.animateContentSize().padding(horizontal = tokens.spacing.md)) {
                if (atTop) {
                    CategoryChips(state.category, state.adultEnabled) { onEvent(DiscoverUiEvent.CategorySelected(it)) }
                    if (state.perProvider.isNotEmpty()) ProviderStatusRow(state.perProvider)
                    if (state.history.isNotEmpty()) {
                        SearchHistoryRow(state.history, onRepeat = { onEvent(DiscoverUiEvent.RepeatSearch(it)) }, onClear = { onEvent(DiscoverUiEvent.ClearHistory) })
                    }
                    Spacer(Modifier.height(tokens.spacing.xs))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (state.overall) {
                    OverallSearchState.LOADING, OverallSearchState.REFRESHING ->
                        if (state.results.isEmpty()) {
                            CenterState { CircularProgressIndicator(); Text("Searching…", style = MaterialTheme.typography.bodyMedium) }
                        } else {
                            ResultsList(state, listState, onEvent)
                        }
                    OverallSearchState.EMPTY ->
                        CenterState(icon = { Icon(Icons.Rounded.Inbox, null, Modifier.size(40.dp)) }, title = "No results", body = "Try a different title or category.")
                    OverallSearchState.ALL_FAILED ->
                        CenterState(icon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(40.dp)) }, title = "All providers failed", body = "Check connectivity and retry.", action = { TextButton(onClick = { onEvent(DiscoverUiEvent.Refresh) }) { Text("Retry") } })
                    OverallSearchState.OFFLINE ->
                        CenterState(icon = { Icon(Icons.Rounded.CloudOff, null, Modifier.size(40.dp)) }, title = "You're offline", body = "Reconnect and retry.")
                    else -> ResultsList(state, listState, onEvent)
                }
                if (atBottom && state.results.isNotEmpty() && (state.overall == OverallSearchState.RESULTS || state.overall == OverallSearchState.PARTIAL)) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Scroll to top") }
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
                onEvent(DiscoverUiEvent.FiltersApplied(sort, min, provs, minMb, maxMb, from, to)); showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun ResultsList(
    state: DiscoverUiState,
    listState: LazyListState,
    onEvent: (DiscoverUiEvent) -> Unit,
) {
    val tokens = LocalPdTokens.current
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = tokens.spacing.md, top = tokens.spacing.sm, end = tokens.spacing.md, bottom = 96.dp),
    ) {
        if (state.isSearching) {
            item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics { contentDescription = "Searching" },
                )
            }
        }
        items(state.results, key = { it.resultId }) { result ->
            ResultCard(
                result = result,
                onOpen = { onEvent(DiscoverUiEvent.OpenDetails(result)) },
                onAdd = { onEvent(DiscoverUiEvent.AddToPullDock(result)) },
                modifier = Modifier.animateItem(),
            )
        }
        if (state.hasMore) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FilterChip(selected = false, onClick = { onEvent(DiscoverUiEvent.LoadMore) }, label = { Text("Load more") })
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onEvent: (DiscoverUiEvent) -> Unit, onFilters: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalPdTokens.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        onEvent(DiscoverUiEvent.Submit)
        focus.clearFocus()
        keyboard?.hide()
    }
    OutlinedTextField(
        value = query,
        onValueChange = { onEvent(DiscoverUiEvent.QueryChanged(it)) },
        placeholder = { Text("Search torrents…") },
        trailingIcon = {
            Row {
                IconButton(onClick = onFilters, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Tune, contentDescription = "Filters") }
                IconButton(onClick = submit, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) }
            }
        },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submit() }),
        shape = RoundedCornerShape(tokens.radius.lg),
        modifier = modifier,
    )
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

private fun SearchCategory.label(): String = when (this) {
    SearchCategory.TV_SERIES -> "TV Series"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}