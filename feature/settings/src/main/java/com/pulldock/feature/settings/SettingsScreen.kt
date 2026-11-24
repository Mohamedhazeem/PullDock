package com.pulldock.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.download.repository.AppearanceTheme

/**
 * Settings screen (T047, US7). Groups present: downloads (concurrency), network
 * (Wi-Fi-only/metered), battery threshold, appearance, privacy (informational),
 * legal (navigation). Provider status and extractor update groups arrive with
 * Slice 4 / the lifecycle group — no dead controls are shipped for them here.
 */
@Composable
fun SettingsScreen(
    onOpenLegal: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        val policy = state.policy
        if (policy == null) {
            PdEmptyState(
                title = stringResource(R.string.settings_loading_title),
                message = stringResource(R.string.settings_loading_body),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Section(stringResource(R.string.settings_section_network)) {
                PolicySwitch(
                    label = stringResource(R.string.settings_wifi_only),
                    description = stringResource(R.string.settings_wifi_only_desc),
                    checked = policy.wifiOnly,
                    onChecked = viewModel::setWifiOnly,
                )
                PolicySwitch(
                    label = stringResource(R.string.settings_metered),
                    description = stringResource(R.string.settings_metered_desc),
                    checked = policy.meteredAllowed,
                    onChecked = viewModel::setMeteredAllowed,
                )
            }

            Section(stringResource(R.string.settings_section_battery)) {
                val thresholdLabel = if (policy.batteryThresholdPercent == 0) {
                    stringResource(R.string.settings_battery_disabled)
                } else {
                    stringResource(R.string.settings_battery_value, policy.batteryThresholdPercent)
                }
                Text(
                    text = thresholdLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { stateDescription = thresholdLabel },
                )
                Slider(
                    value = policy.batteryThresholdPercent.toFloat(),
                    onValueChange = { viewModel.setBatteryThreshold(it.toInt()) },
                    valueRange = 0f..50f,
                    steps = 9,
                )
            }

            Section(stringResource(R.string.settings_section_downloads)) {
                Text(
                    text = stringResource(
                        R.string.settings_concurrency_value,
                        policy.maxConcurrent,
                        policy.torrentMaxConcurrent,
                        policy.extractorMaxConcurrent,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButtonCompat(label = stringResource(R.string.settings_concurrency_default)) {
                    viewModel.setConcurrency(global = 3, torrent = 2, extractor = 1)
                }

                Text(
                    text = stringResource(R.string.settings_folder_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val folders = listOf(
                    null to stringResource(R.string.settings_folder_default),
                    "Downloads" to stringResource(R.string.settings_folder_downloads),
                    "Movies" to stringResource(R.string.settings_folder_movies),
                    "Music" to stringResource(R.string.settings_folder_music),
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                ) {
                    items(folders) { (path, label) ->
                        FilterChip(
                            selected = state.downloadFolder == path,
                            onClick = { viewModel.setDownloadFolder(path) },
                            label = { Text(label) },
                        )
                    }
                }
                var customFolder by rememberSaveable(state.downloadFolder) {
                    mutableStateOf(state.downloadFolder ?: "")
                }
                OutlinedTextField(
                    value = customFolder,
                    onValueChange = { customFolder = it },
                    label = { Text(stringResource(R.string.settings_folder_custom)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButtonCompat(label = stringResource(R.string.settings_folder_apply)) {
                    viewModel.setDownloadFolder(customFolder.trim().ifBlank { null })
                }

                Text(
                    text = stringResource(R.string.settings_quality_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                    items(listOf(null, 360, 480, 720, 1080)) { height ->
                        FilterChip(
                            selected = state.defaultQualityHeight == height,
                            onClick = { viewModel.setDefaultQuality(height) },
                            label = { Text(if (height == null) stringResource(R.string.settings_quality_auto) else "${height}p") },
                        )
                    }
                }
            }
        }

        Section(stringResource(R.string.settings_section_appearance)) {
            val options = listOf(
                AppearanceTheme.SYSTEM to stringResource(R.string.settings_theme_system),
                AppearanceTheme.LIGHT to stringResource(R.string.settings_theme_light),
                AppearanceTheme.DARK to stringResource(R.string.settings_theme_dark),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (theme, label) ->
                    SegmentedButton(
                        selected = state.theme == theme,
                        onClick = { viewModel.setTheme(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) },
                    )
                }
            }
        }

        Section(stringResource(R.string.settings_section_providers)) {
            Text(
                text = stringResource(R.string.settings_providers_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.providers.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_providers_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.providers.forEach { (id, health) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = id.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = health.state.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Section(stringResource(R.string.settings_section_privacy)) {
            Text(
                text = stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(stringResource(R.string.settings_section_legal)) {
            TextButtonCompat(label = stringResource(R.string.settings_open_legal), onClick = onOpenLegal)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val tokens = LocalPdTokens.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PolicySwitch(label: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun TextButtonCompat(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}
