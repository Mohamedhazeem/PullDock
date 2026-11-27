package com.pulldock.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdBarChart
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdLoadingState
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdSettingRow
import com.pulldock.core.designsystem.components.PdStatData
import com.pulldock.core.designsystem.components.PdStatGrid
import com.pulldock.core.designsystem.components.PdUsageBar
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.ProviderId
import com.pulldock.core.model.formatBytes
import com.pulldock.domain.download.model.UsageRange
import com.pulldock.domain.download.repository.AppearanceTheme
import java.time.format.DateTimeFormatter

/** Settings: short labels and icons; long explanations live in Legal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenLegal: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PdScaffold(
        title = stringResource(R.string.settings_title),
        showTopBar = false,
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
                    .padding(horizontal = tokens.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
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
                        ChipGrid2(
                            items = folders,
                            isSelected = { state.downloadFolder == it.first },
                            label = { it.second },
                            onSelect = { viewModel.setDownloadFolder(it.first) },
                        )
                        val folderPicker = rememberLauncherForActivityResult(
                            ActivityResultContracts.OpenDocumentTree(),
                        ) { uri ->
                            uri?.lastPathSegment
                                ?.substringAfterLast(':')
                                ?.takeIf { it.isNotBlank() }
                                ?.let(viewModel::setDownloadFolder)
                        }
                        OutlinedButton(
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(tokens.spacing.sm))
                            Text(
                                text = state.downloadFolder
                                    ?: stringResource(R.string.settings_folder_choose),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val context = LocalContext.current
                        OutlinedButton(
                            onClick = {
                                val grant = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + context.packageName),
                                )
                                runCatching { context.startActivity(grant) }.onFailure {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:" + context.packageName)
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(tokens.spacing.sm))
                            Text(text = stringResource(R.string.settings_grant_all_files))
                        }
                        Text(
                            text = stringResource(R.string.settings_quality_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val qualityOptions = listOf(
                            null to stringResource(R.string.settings_quality_auto),
                            360 to "360p",
                            480 to "480p",
                            720 to "720p",
                            1080 to "1080p",
                        )
                        ChipGrid2(
                            items = qualityOptions,
                            isSelected = { state.defaultQualityHeight == it.first },
                            label = { it.second },
                            onSelect = { viewModel.setDefaultQuality(it.first) },
                        )
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
                    UsageSection(state = state, onRange = viewModel::setUsageRange)
                }
                item {
                    Section(title = stringResource(R.string.settings_section_providers), icon = Icons.Rounded.Cloud) {
                        ProviderGrid(state = state)
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
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
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
private fun <T> ChipGrid2(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val tokens = LocalPdTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                row.forEach { item ->
                    FilterChip(
                        selected = isSelected(item),
                        onClick = { onSelect(item) },
                        label = { Text(label(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UsageSection(
    state: SettingsUiState,
    onRange: (UsageRange) -> Unit,
) {
    val tokens = LocalPdTokens.current
    Section(title = stringResource(R.string.settings_section_usage), icon = Icons.Rounded.BarChart) {
        val ranges = UsageRange.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ranges.forEachIndexed { index, range ->
                SegmentedButton(
                    selected = state.usageRange == range,
                    onClick = { onRange(range) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                    label = { Text(rangeLabel(range)) },
                )
            }
        }
        val bars = state.usage.map { day ->
            PdUsageBar(
                label = day.day.format(DAY_FORMATTER),
                wifiBytes = day.wifiBytes,
                mobileBytes = day.mobileBytes,
            )
        }
        val total = state.usage.sumOf { it.totalBytes }
        val wifi = state.usage.sumOf { it.wifiBytes }
        val mobile = state.usage.sumOf { it.mobileBytes }
        val active = state.usage.sumOf { it.activeTransferMs }
        val open = state.usage.sumOf { it.appOpenMs }
        PdBarChart(
            bars = bars,
            contentDescription = stringResource(
                R.string.settings_usage_chart_desc,
                formatBytes(total),
                formatBytes(wifi),
                formatBytes(mobile),
            ),
            emptyLabel = stringResource(R.string.settings_usage_empty),
        )
        PdStatGrid(
            stats = listOf(
                PdStatData(stringResource(R.string.settings_usage_total), formatBytes(total)),
                PdStatData(stringResource(R.string.settings_usage_wifi), formatBytes(wifi)),
                PdStatData(stringResource(R.string.settings_usage_mobile), formatBytes(mobile)),
                PdStatData(stringResource(R.string.settings_usage_active), formatDuration(active)),
                PdStatData(stringResource(R.string.settings_usage_open), formatDuration(open)),
            ),
        )
        Spacer(Modifier.size(tokens.spacing.xs))
    }
}

@Composable
private fun ProviderGrid(state: SettingsUiState) {
    val tokens = LocalPdTokens.current
    val runtimeText = stringResource(
        if (state.runtime?.runtimeCompatible == true) {
            R.string.settings_runtime_verified
        } else {
            R.string.settings_runtime_unverified
        },
    )
    ProviderId.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            row.forEach { id ->
                val supported = providerSupported(id)
                PdCard(modifier = Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                        Text(
                            text = id.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (supported) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    ),
                            )
                            Spacer(Modifier.width(tokens.spacing.xs))
                            Text(
                                text = stringResource(
                                    if (supported) {
                                        R.string.settings_provider_active
                                    } else {
                                        R.string.settings_provider_inactive
                                    },
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = runtimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** All current providers are recognized; a removed/unknown id would be unsupported. */
private fun providerSupported(id: ProviderId): Boolean = id in ProviderId.entries

@Composable
private fun rangeLabel(range: UsageRange): String = when (range) {
    UsageRange.DAY -> stringResource(R.string.settings_range_day)
    UsageRange.THREE_DAYS -> stringResource(R.string.settings_range_3days)
    UsageRange.WEEK -> stringResource(R.string.settings_range_week)
    UsageRange.MONTH -> stringResource(R.string.settings_range_month)
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "%dh %dm".format(hours, minutes)
        minutes > 0 -> "%dm".format(minutes)
        else -> "%ds".format(totalSeconds)
    }
}

private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")