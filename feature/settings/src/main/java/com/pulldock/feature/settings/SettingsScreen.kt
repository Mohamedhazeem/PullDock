package com.pulldock.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdLoadingState
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdSettingRow
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.download.repository.AppearanceTheme
import com.pulldock.domain.extractor.model.ProviderHealthState

/** Settings: short labels and icons; long explanations live in Legal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLegal: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    PdScaffold(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        val policy = state.policy
        if (policy == null) {
            PdLoadingState(
                label = stringResource(R.string.settings_loading_title),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = tokens.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                item {
                    Section(title = stringResource(R.string.settings_section_downloads), icon = Icons.Rounded.Download) {
                        PolicyStepper(
                            label = stringResource(R.string.settings_concurrency_total),
                            value = policy.maxConcurrent,
                            onMinus = {
                                viewModel.setConcurrency(
                                    global = (policy.maxConcurrent - 1).coerceAtLeast(1),
                                    torrent = policy.torrentMaxConcurrent,
                                    extractor = policy.extractorMaxConcurrent,
                                )
                            },
                            onPlus = {
                                viewModel.setConcurrency(
                                    global = (policy.maxConcurrent + 1).coerceAtMost(10),
                                    torrent = policy.torrentMaxConcurrent,
                                    extractor = policy.extractorMaxConcurrent,
                                )
                            },
                        )
                        PolicyStepper(
                            label = stringResource(R.string.settings_concurrency_torrents),
                            value = policy.torrentMaxConcurrent,
                            onMinus = {
                                viewModel.setConcurrency(
                                    global = policy.maxConcurrent,
                                    torrent = (policy.torrentMaxConcurrent - 1).coerceAtLeast(1),
                                    extractor = policy.extractorMaxConcurrent,
                                )
                            },
                            onPlus = {
                                viewModel.setConcurrency(
                                    global = policy.maxConcurrent,
                                    torrent = (policy.torrentMaxConcurrent + 1).coerceAtMost(10),
                                    extractor = policy.extractorMaxConcurrent,
                                )
                            },
                        )
                        PolicyStepper(
                            label = stringResource(R.string.settings_concurrency_media),
                            value = policy.extractorMaxConcurrent,
                            onMinus = {
                                viewModel.setConcurrency(
                                    global = policy.maxConcurrent,
                                    torrent = policy.torrentMaxConcurrent,
                                    extractor = (policy.extractorMaxConcurrent - 1).coerceAtLeast(1),
                                )
                            },
                            onPlus = {
                                viewModel.setConcurrency(
                                    global = policy.maxConcurrent,
                                    torrent = policy.torrentMaxConcurrent,
                                    extractor = (policy.extractorMaxConcurrent + 1).coerceAtMost(10),
                                )
                            },
                        )
                        TextButtonCompat(label = stringResource(R.string.settings_concurrency_default)) {
                            viewModel.setConcurrency(global = 3, torrent = 2, extractor = 1)
                        }
                        Text(
                            text = stringResource(R.string.settings_folder_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val folders = listOf(
                            null to stringResource(R.string.settings_folder_default),
                            "Downloads" to stringResource(R.string.settings_folder_downloads),
                            "Movies" to stringResource(R.string.settings_folder_movies),
                            "Music" to stringResource(R.string.settings_folder_music),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                            items(folders) { (path, label) ->
                                androidx.compose.material3.FilterChip(
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
                            shape = RoundedCornerShape(tokens.radius.md),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButtonCompat(label = stringResource(R.string.settings_folder_apply)) {
                            viewModel.setDownloadFolder(customFolder.trim().ifBlank { null })
                        }
                        Text(
                            text = stringResource(R.string.settings_quality_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                            items(listOf(null, 360, 480, 720, 1080)) { height ->
                                androidx.compose.material3.FilterChip(
                                    selected = state.defaultQualityHeight == height,
                                    onClick = { viewModel.setDefaultQuality(height) },
                                    label = {
                                        Text(
                                            if (height == null) {
                                                stringResource(R.string.settings_quality_auto)
                                            } else {
                                                "${height}p"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Section(title = stringResource(R.string.settings_section_network), icon = Icons.Rounded.NetworkCheck) {
                        PolicySwitch(
                            label = stringResource(R.string.settings_wifi_only),
                            icon = Icons.Rounded.Wifi,
                            checked = policy.wifiOnly,
                            onChecked = viewModel::setWifiOnly,
                        )
                        PolicySwitch(
                            label = stringResource(R.string.settings_metered),
                            icon = Icons.Rounded.Speed,
                            checked = policy.meteredAllowed,
                            onChecked = viewModel::setMeteredAllowed,
                        )
                    }
                }
                item {
                    Section(title = stringResource(R.string.settings_section_battery), icon = Icons.Rounded.BatterySaver) {
                        Text(
                            text = if (policy.batteryThresholdPercent == 0) {
                                stringResource(R.string.settings_battery_disabled)
                            } else {
                                stringResource(R.string.settings_battery_value, policy.batteryThresholdPercent)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = policy.batteryThresholdPercent.toFloat(),
                            onValueChange = { viewModel.setBatteryThreshold(it.toInt()) },
                            valueRange = 0f..50f,
                            steps = 9,
                        )
                    }
                }
                item {
                    Section(title = stringResource(R.string.settings_section_appearance), icon = Icons.Rounded.Palette) {
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
                }
                item {
                    Section(title = stringResource(R.string.settings_section_providers), icon = Icons.Rounded.Cloud) {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(providerColor(health.state)),
                                    )
                                    Text(
                                        text = id.name.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = providerLabel(health.state),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Section(title = stringResource(R.string.settings_section_privacy), icon = Icons.Rounded.Shield) {
                        Text(
                            text = stringResource(R.string.settings_privacy_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Section(title = stringResource(R.string.settings_section_legal), icon = Icons.Rounded.Gavel) {
                        TextButtonCompat(label = stringResource(R.string.settings_open_legal), onClick = onOpenLegal)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    icon: ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tokens = LocalPdTokens.current
    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(tokens.radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(tokens.spacing.sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            content()
        }
    }
}

@Composable
private fun PolicySwitch(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    PdSettingRow(
        title = label,
        icon = icon,
        trailing = {
            Switch(checked = checked, onCheckedChange = onChecked)
        },
    )
}

@Composable
private fun PolicyStepper(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    PdSettingRow(
        title = label,
        trailing = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp),
                ) {
                    FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = stringResource(R.string.settings_decrease),
                        )
                    }
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(36.dp),
                    )
                    FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.settings_increase),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun TextButtonCompat(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label) }
}

@Composable
private fun providerLabel(state: ProviderHealthState): String = when (state) {
    ProviderHealthState.CURRENT -> stringResource(R.string.settings_provider_current)
    ProviderHealthState.UPDATE_RECOMMENDED -> stringResource(R.string.settings_provider_update_recommended)
    ProviderHealthState.UPDATE_REQUIRED -> stringResource(R.string.settings_provider_update_required)
    ProviderHealthState.PLATFORM_CHANGE_SUSPECTED -> stringResource(R.string.settings_provider_platform_change)
    ProviderHealthState.PROVIDER_TEMPORARILY_UNAVAILABLE -> stringResource(R.string.settings_provider_unavailable)
    ProviderHealthState.PROVIDER_UNSUPPORTED -> stringResource(R.string.settings_provider_unsupported)
    ProviderHealthState.AUTHORIZATION_REQUIRED -> stringResource(R.string.settings_provider_auth_required)
    ProviderHealthState.VERSION_CHECK_UNAVAILABLE -> stringResource(R.string.settings_provider_unknown)
    ProviderHealthState.RUNTIME_INCOMPATIBLE -> stringResource(R.string.settings_provider_runtime)
}

private fun providerColor(state: ProviderHealthState): androidx.compose.ui.graphics.Color = when (state) {
    ProviderHealthState.CURRENT -> androidx.compose.ui.graphics.Color(0xFF226C3B)
    ProviderHealthState.UPDATE_RECOMMENDED, ProviderHealthState.VERSION_CHECK_UNAVAILABLE ->
        androidx.compose.ui.graphics.Color(0xFF8C5300)
    ProviderHealthState.UPDATE_REQUIRED, ProviderHealthState.RUNTIME_INCOMPATIBLE,
    ProviderHealthState.PROVIDER_UNSUPPORTED -> androidx.compose.ui.graphics.Color(0xFFB3261E)
    else -> androidx.compose.ui.graphics.Color(0xFF625C54)
}