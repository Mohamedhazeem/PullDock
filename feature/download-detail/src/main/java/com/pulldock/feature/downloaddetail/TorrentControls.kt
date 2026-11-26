package com.pulldock.feature.downloaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.components.PdButton
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdSectionHeader
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.formatBytes
import com.pulldock.domain.torrent.model.FileSelection
import com.pulldock.domain.torrent.model.SeedingPolicy
import com.pulldock.domain.torrent.model.TorrentTaskDetails

private val SelectedIndexesSaver = Saver<Set<Int>, List<Int>>(
    save = { it.sorted() },
    restore = { value -> value.toSet() },
)

/**
 * Torrent-specific detail controls: file selection with per-file size, and seeding
 * policy controls. Both apply through the domain use cases.
 */
@Composable
internal fun TorrentFileSelectionSection(
    details: TorrentTaskDetails,
    onApply: (FileSelection) -> Unit,
) {
    val tokens = LocalPdTokens.current
    var selected by rememberSaveable(stateSaver = SelectedIndexesSaver) {
        mutableStateOf(details.files.filter { it.selected }.map { it.index }.toSet())
    }

    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            PdSectionHeader(
                title = stringResource(R.string.detail_files_section),
                action = {
                    Row {
                        TextButton(onClick = { selected = details.files.map { it.index }.toSet() }) {
                            Text(stringResource(R.string.detail_select_all))
                        }
                        TextButton(onClick = { selected = emptySet() }) {
                            Text(stringResource(R.string.detail_clear_selection))
                        }
                    }
                },
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                items(details.files, key = { it.index }) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = file.index in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + file.index else selected - file.index
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
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
            PdButton(
                label = stringResource(R.string.detail_apply_selection),
                onClick = { onApply(FileSelection(selectedIndexes = selected)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun SeedingControlsSection(
    details: TorrentTaskDetails,
    onApply: (SeedingPolicy) -> Unit,
) {
    val tokens = LocalPdTokens.current
    val policy = details.seedingPolicy
    var seedAfterCompletion by rememberSaveable(details.taskId) {
        mutableStateOf(policy.seedAfterCompletion)
    }

    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            PdSectionHeader(title = stringResource(R.string.detail_seeding_section))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_seed_after_completion),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
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
}