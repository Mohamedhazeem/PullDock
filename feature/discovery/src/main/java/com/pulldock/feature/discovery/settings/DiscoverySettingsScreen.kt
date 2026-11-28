package com.pulldock.feature.discovery.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.feature.discovery.DiscoverUiEvent
import com.pulldock.feature.discovery.DiscoverUiState
import com.pulldock.feature.discovery.DiscoverViewModel

/** Discovery settings: history/cache, Torznab endpoints, and a guarded NSFW opt-in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverySettingsScreen(
    state: DiscoverUiState,
    onEvent: (DiscoverUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Discovery settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            GroupCard("Content") {
                SwitchRow("Save search history", state.historyEnabled) { onEvent(DiscoverUiEvent.SetHistoryEnabled(it)) }
                SwitchRow("The Pirate Bay (unofficial)", state.tpbEnabled) { onEvent(DiscoverUiEvent.ToggleTpb) }
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                    TextButton(onClick = { onEvent(DiscoverUiEvent.ClearHistory) }) { Text("Clear history") }
                    TextButton(onClick = { onEvent(DiscoverUiEvent.ClearCache) }) { Text("Clear cache") }
                }
            }

            GroupCard("Torznab indexers") {
                Text("Predefined and custom indexers. Remove any you don't want.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.torznabConfigs.forEach { config -> TorznabRow(config) { onEvent(DiscoverUiEvent.RemoveTorznab(config.providerId)) } }
                var showAdd by remember { mutableStateOf(false) }
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                    TextButton(onClick = { onEvent(DiscoverUiEvent.RestoreTorznabDefaults) }) { Text("Restore defaults") }
                    TextButton(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Close" else "Add indexer") }
                }
                if (showAdd) AddTorznabForm(onEvent)
            }

            Text(
                "Searches go only to the providers you enable. Queries never reach a PullDock server (none exists). API keys stay in memory and are never persisted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        }
    }
}

@Composable
private fun GroupCard(title: String?, content: @Composable () -> Unit) {
    val tokens = LocalPdTokens.current
    Surface(shape = RoundedCornerShape(tokens.radius.lg), tonalElevation = 1.dp) {
        Column(Modifier.padding(tokens.spacing.md), verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TorznabRow(config: ProviderConfiguration, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(config.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(config.endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun AddTorznabForm(onEvent: (DiscoverUiEvent) -> Unit) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS API endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key (optional, session only)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        if (endpoint.startsWith("https://")) {
            onEvent(DiscoverUiEvent.AddTorznab(ProviderConfiguration("torznab-" + endpoint.hashCode(), name.ifBlank { "Torznab" }, endpoint.trim(), true), apiKey.ifBlank { null }))
            name = ""; endpoint = ""; apiKey = ""
        }
    }) { Text("Add indexer") }
}

/** Route wrapper obtaining the shared Discover ViewModel for settings actions. */
@Composable
fun DiscoverySettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DiscoverViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    DiscoverySettingsScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}