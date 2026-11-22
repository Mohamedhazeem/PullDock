package com.pulldock.feature.downloaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pulldock.core.designsystem.components.PdButton
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.torrent.model.FileSelection
import com.pulldock.domain.torrent.model.SeedingPolicy
import com.pulldock.domain.torrent.model.TorrentTaskDetails

/**
 * Torrent-specific detail controls (T057/T067): file selection with per-file size,
 * and seeding policy controls (FR-011). Both apply through the domain use cases; the
 * UI is state-driven and every control has a real effect (no dead controls, FR-070).
 */
@Composable
internal fun TorrentFileSelectionSection(
    details: TorrentTaskDetails,
    onApply: (FileSelection) -> Unit,
) {
    val tokens = LocalPdTokens.current
    var selected by remember(details.taskId) {
        mutableStateOf(details.files.filter { it.selected }.map { it.index }.toSet())
    }

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text(stringResource(R.string.detail_files_section), style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(details.files, key = { it.index }) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(modifier = Modifier.weight(1f)) {
                        Checkbox(
                            checked = file.index in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + file.index else selected - file.index
                            },
                        )
                        Column {
                            Text(
                                text = file.path.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = formatBytes(file.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        PdButton(
            label = stringResource(R.string.detail_apply_selection),
            onClick = { onApply(FileSelection(selectedIndexes = selected)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SeedingControlsSection(
    details: TorrentTaskDetails,
    onApply: (SeedingPolicy) -> Unit,
) {
    val tokens = LocalPdTokens.current
    val policy = details.seedingPolicy
    var seedAfterCompletion by remember(details.taskId) { mutableStateOf(policy.seedAfterCompletion) }

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text(stringResource(R.string.detail_seeding_section), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.detail_seed_after_completion),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = seedAfterCompletion,
                onCheckedChange = { seedAfterCompletion = it },
            )
        }
        Text(
            text = stringResource(R.string.detail_seeding_stop_note, policy.stopRatio ?: 1.0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PdButton(
            label = stringResource(R.string.detail_apply_seeding),
            onClick = {
                onApply(
                    policy.copy(
                        seedAfterCompletion = seedAfterCompletion,
                        perTorrentOverride = true,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024.0 -> "%.1f GB".format(mb / 1024.0)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%d KB".format(bytes / 1024)
    }
}
