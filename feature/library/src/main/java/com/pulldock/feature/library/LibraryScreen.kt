package com.pulldock.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.formatBytes
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.LibrarySection
import com.pulldock.domain.download.repository.LibrarySort
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Library screen (T156, US5/FR-042): sections, search, sort, playback, reconciliation. */
@Composable
fun LibraryScreen(
    onPlay: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = viewModel::reconcile) { Text(stringResource(R.string.library_reconcile)) }
        }

        OutlinedTextField(
            value = state.query.searchText.orEmpty(),
            onValueChange = viewModel::setSearch,
            label = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            items(LibrarySection.entries) { section ->
                FilterChip(
                    selected = state.query.section == section,
                    onClick = { viewModel.setSection(section) },
                    label = { Text(sectionLabel(section)) },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { viewModel.setSort(nextSort(state.query.sortBy)) },
                    label = { Text(stringResource(R.string.library_sort, sortLabel(state.query.sortBy))) },
                )
            }
        }

        state.lastReconciled?.let { missing ->
            Text(
                text = stringResource(R.string.library_reconcile_result, missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!state.loading && state.items.isEmpty()) {
            PdEmptyState(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                items(state.items, key = { it.id }) { item ->
                    LibraryItemCard(
                        item = item,
                        onPlay = { onPlay(item.id) },
                        onDelete = { viewModel.requestDelete(item.id) },
                    )
                }
            }
        }
    }

    state.pendingDelete?.let {
        PdConfirmDialog(
            title = stringResource(R.string.library_delete_title),
            text = stringResource(R.string.library_delete_body),
            confirmLabel = stringResource(R.string.library_delete_confirm),
            dismissLabel = stringResource(R.string.library_delete_dismiss),
            onConfirm = { viewModel.confirmDelete(deleteFile = true) },
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun LibraryItemCard(item: LibraryItem, onPlay: () -> Unit, onDelete: () -> Unit) {
    val tokens = LocalPdTokens.current
    val playLabel = stringResource(R.string.library_play, item.title)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        item.sizeBytes?.let { append(formatBytes(it)) }
                        append(" · ")
                        append(dateFormatter.format(item.completedAt.atZone(ZoneId.systemDefault())))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!item.fileAvailable) {
                    Text(
                        stringResource(R.string.library_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(
                onClick = onPlay,
                enabled = item.fileAvailable,
                modifier = Modifier.semantics { contentDescription = playLabel },
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = playLabel)
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.library_delete)) }
        }
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@Composable
private fun sectionLabel(section: LibrarySection): String = when (section) {
    LibrarySection.ALL -> stringResource(R.string.library_section_all)
    LibrarySection.VIDEO -> stringResource(R.string.library_section_video)
    LibrarySection.AUDIO -> stringResource(R.string.library_section_audio)
    LibrarySection.OTHER -> stringResource(R.string.library_section_other)
}

@Composable
private fun sortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.NEWEST_FIRST -> stringResource(R.string.library_sort_newest)
    LibrarySort.OLDEST_FIRST -> stringResource(R.string.library_sort_oldest)
    LibrarySort.TITLE_ASC -> stringResource(R.string.library_sort_title)
    LibrarySort.TITLE_DESC -> stringResource(R.string.library_sort_title_desc)
    LibrarySort.SIZE_DESC -> stringResource(R.string.library_sort_size)
}

private fun nextSort(sort: LibrarySort): LibrarySort = when (sort) {
    LibrarySort.NEWEST_FIRST -> LibrarySort.TITLE_ASC
    LibrarySort.TITLE_ASC -> LibrarySort.SIZE_DESC
    LibrarySort.SIZE_DESC -> LibrarySort.OLDEST_FIRST
    LibrarySort.OLDEST_FIRST -> LibrarySort.TITLE_DESC
    LibrarySort.TITLE_DESC -> LibrarySort.NEWEST_FIRST
}
