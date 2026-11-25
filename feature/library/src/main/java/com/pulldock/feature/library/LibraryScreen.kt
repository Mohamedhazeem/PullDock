package com.pulldock.feature.library

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdFilterChips
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdSearchField
import com.pulldock.core.designsystem.components.PdSkeleton
import com.pulldock.core.designsystem.components.PdStatusBadge
import com.pulldock.core.designsystem.components.PdStatusTone
import com.pulldock.core.designsystem.components.ThumbnailImage
import com.pulldock.core.designsystem.components.pdLibraryIcon
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.formatBytes
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.LibrarySection
import com.pulldock.domain.download.repository.LibrarySort
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Library screen: search, sections, sort, playback, and reconciliation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPlay: (String) -> Unit,
    onAddDownload: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sectionOptions = LibrarySection.entries.map { sectionLabel(it) }

    PdScaffold(
        title = stringResource(R.string.library_title),
        snackbarHostState = snackbar,
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(onClick = viewModel::reconcile) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.library_reconcile),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = tokens.layout.gridMinWidth),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PdSearchField(
                    value = state.query.searchText.orEmpty(),
                    onValueChange = viewModel::setSearch,
                    placeholder = stringResource(R.string.library_search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PdFilterChips(
                    options = sectionOptions,
                    selected = sectionLabel(state.query.section),
                    onSelect = { selected ->
                        val index = sectionOptions.indexOf(selected)
                        viewModel.setSection(
                            LibrarySection.entries.getOrNull(index) ?: state.query.section,
                        )
                    },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                ) {
                    Text(
                        text = sortLabel(state.query.sortBy),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(tokens.spacing.xs))
                            Text(stringResource(R.string.library_sort_label))
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(sort)) },
                                    onClick = {
                                        viewModel.setSort(sort)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            state.lastReconciled?.let { missing ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.library_reconcile_result, missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.loading) {
                items(6) { LibrarySkeletonCard() }
            } else if (state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PdEmptyState(
                        title = stringResource(R.string.library_empty_title),
                        message = stringResource(R.string.library_empty_body),
                        icon = Icons.Rounded.PlayArrow,
                        actionLabel = stringResource(R.string.library_add),
                        onAction = onAddDownload,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { "library_item" },
                ) { item ->
                    LibraryItemCard(
                        item = item,
                        onPlay = { onPlay(item.id) },
                        onDelete = { viewModel.requestDelete(item.id) },
                        modifier = Modifier.animateItem(),
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
private fun LibraryItemCard(
    item: LibraryItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    val context = LocalContext.current
    val playLabel = stringResource(R.string.library_play, item.title)
    val shareLabel = stringResource(R.string.library_share)
    PdCard(
        onClick = if (item.fileAvailable) onPlay else null,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThumbnailImage(
                uri = item.thumbnailUri,
                fallback = pdLibraryIcon(item.fileUri),
                contentDescription = item.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(tokens.radius.md))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(Modifier.width(tokens.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = buildString {
                        item.sizeBytes?.let { append(formatBytes(it)) }
                        if (item.sizeBytes != null) append(" · ")
                        append(dateFormatter.format(item.completedAt.atZone(ZoneId.systemDefault())))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (!item.fileAvailable) {
                    PdStatusBadge(
                        label = stringResource(R.string.library_unavailable),
                        icon = Icons.Rounded.ErrorOutline,
                        tone = PdStatusTone.Error,
                    )
                }
            }
            IconButton(
                onClick = onPlay,
                enabled = item.fileAvailable,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = playLabel,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = { shareFile(context, item) },
                enabled = item.fileAvailable,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = shareLabel,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.library_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LibrarySkeletonCard() {
    val tokens = LocalPdTokens.current
    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            PdSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            PdSkeleton(modifier = Modifier.fillMaxWidth(0.7f))
            PdSkeleton(modifier = Modifier.fillMaxWidth(0.4f))
        }
    }
}

private fun shareFile(context: android.content.Context, item: LibraryItem) {
    val file = File(item.fileUri.removePrefix("file://"))
    if (!file.isFile) return
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.library_share_title)))
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