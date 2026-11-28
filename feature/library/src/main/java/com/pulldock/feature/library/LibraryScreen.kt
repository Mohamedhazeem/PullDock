package com.pulldock.feature.library

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pulldock.core.designsystem.components.PdExpandableGroup
import com.pulldock.core.designsystem.components.PdFilterChips
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdSearchField
import com.pulldock.core.designsystem.components.PdSkeleton
import com.pulldock.core.designsystem.components.PdStatusBadge
import com.pulldock.core.designsystem.components.PdStatusTone
import com.pulldock.core.designsystem.components.ThumbnailImage
import com.pulldock.core.designsystem.components.pdLibraryIcon
import com.pulldock.core.designsystem.components.pdTypeIcon
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.MediaKind
import com.pulldock.core.model.formatBytes
import com.pulldock.core.model.mediaKindOf
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
    var gridMode by remember { mutableStateOf(false) }
    val sectionOptions = LibrarySection.entries.map { sectionLabel(it) }

    // Detect files removed outside the app (e.g. deleted from the gallery) on open.
    LaunchedEffect(Unit) { viewModel.reconcile() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    PdScaffold(
        title = stringResource(R.string.library_title),
        snackbarHostState = snackbar,
        scrollBehavior = scrollBehavior,
        showTopBar = false,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = if (gridMode) GridCells.Fixed(2) else GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            userScrollEnabled = !state.isEmpty,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PdSearchField(
                    value = state.query.searchText.orEmpty(),
                    onValueChange = viewModel::setSearch,
                    placeholder = stringResource(R.string.library_search),
                    onSearch = { },
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
                    IconButton(onClick = { gridMode = !gridMode }) {
                        Icon(
                            imageVector = if (gridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.library_layout_toggle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            } else if (state.isEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        PdEmptyState(
                            title = stringResource(R.string.library_empty_title),
                            message = stringResource(R.string.library_empty_body),
                            icon = Icons.Rounded.PlayArrow,
                            actionLabel = stringResource(R.string.library_add),
                            onAction = onAddDownload,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                items(
                    items = state.groups,
                    key = { "group:" + it.group.id },
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = { "group" },
                ) { group ->
                    LibraryGroupCard(
                        group = group,
                        gridMode = gridMode,
                        onPlay = { onPlay(it) },
                        onDelete = { viewModel.requestDelete(it) },
                        onDeleteGroup = { viewModel.requestDeleteGroup(it) },
                        modifier = Modifier.animateItem(),
                    )
                }
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { "library_item" },
                ) { item ->
                    if (gridMode) {
                        LibraryGridCard(
                            item = item,
                            onPlay = { onPlay(item.id) },
                            onDelete = { viewModel.requestDelete(item.id) },
                            modifier = Modifier.animateItem(),
                        )
                    } else {
                        LibraryItemCard(
                            item = item,
                            onPlay = { onPlay(item.id) },
                            onDelete = { viewModel.requestDelete(item.id) },
                            onExtractAudio = { viewModel.extractAudio(item.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
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

    state.pendingGroupDelete?.let {
        PdConfirmDialog(
            title = stringResource(R.string.library_group_delete_title),
            text = stringResource(R.string.library_group_delete_body),
            confirmLabel = stringResource(R.string.library_group_delete_confirm),
            dismissLabel = stringResource(R.string.library_delete_dismiss),
            onConfirm = { viewModel.confirmGroupDelete(deleteFile = true) },
            onDismiss = viewModel::dismissGroupDelete,
        )
    }
}

@Composable
private fun LibraryGroupCard(
    group: LibraryGroup,
    gridMode: Boolean,
    onPlay: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    var expanded by rememberSaveable(group.group.id) { mutableStateOf(false) }
    val subtitle = buildString {
        append(stringResource(R.string.library_group_items, group.items.size))
        append(" · ").append(formatBytes(group.totalBytes))
    }
    PdExpandableGroup(
        title = group.group.title,
        subtitle = subtitle,
        icon = pdTypeIcon(group.group.type),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        toggleLabel = stringResource(
            if (expanded) R.string.library_group_hide else R.string.library_group_show,
            group.group.title,
        ),
        expandedLabel = stringResource(R.string.library_group_expanded),
        collapsedLabel = stringResource(R.string.library_group_collapsed),
        modifier = modifier,
    ) {
        if (gridMode) {
            group.items.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                    rowItems.forEach { item ->
                        LibraryGridCard(
                            item = item,
                            onPlay = { onPlay(item.id) },
                            onDelete = { onDelete(item.id) },
                            flat = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            group.items.forEach { item ->
                LibraryItemCard(
                    item = item,
                    onPlay = { onPlay(item.id) },
                    onDelete = { onDelete(item.id) },
                    flat = true,
                )
            }
        }
        OutlinedButton(
            onClick = { onDeleteGroup(group.group.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(stringResource(R.string.library_group_delete_confirm))
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: LibraryItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    flat: Boolean = false,
    onExtractAudio: () -> Unit = {},
) {
    val tokens = LocalPdTokens.current
    val context = LocalContext.current
    val playLabel = stringResource(R.string.library_play, item.title)
    val openLabel = stringResource(R.string.library_open, item.title)
    val shareLabel = stringResource(R.string.library_share)
    val playable = mediaKindOf(item.fileUri).playable
    val onPrimary: () -> Unit = if (playable) onPlay else ({ openFile(context, item) })
    PdCard(
        onClick = if (item.fileAvailable) onPrimary else null,
        modifier = modifier,
        flat = flat,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThumbnailImage(
                    uri = item.thumbnailUri,
                    fallback = pdLibraryIcon(item.fileUri),
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(tokens.radius.md))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Spacer(Modifier.width(tokens.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                    )
                    Text(
                        text = buildString {
                            item.sizeBytes?.let { append(formatBytes(it)) }
                            item.resolution?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                            if (item.sizeBytes != null || item.resolution != null) append(" · ")
                            append(dateFormatter.format(item.completedAt.atZone(ZoneId.systemDefault())))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (item.fileAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CardActions(
                        item = item,
                        onPrimary = onPrimary,
                        onDelete = onDelete,
                        primaryLabel = if (playable) playLabel else openLabel,
                        shareLabel = shareLabel,
                        playable = playable,
                        onExtractAudio = onExtractAudio,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                ) {
                    PdStatusBadge(
                        label = stringResource(R.string.library_unavailable),
                        icon = Icons.Rounded.ErrorOutline,
                        tone = PdStatusTone.Error,
                    )
                    Spacer(Modifier.weight(1f))
                    CardActions(
                        item = item,
                        onPrimary = onPrimary,
                        onDelete = onDelete,
                        primaryLabel = if (playable) playLabel else openLabel,
                        shareLabel = shareLabel,
                        playable = playable,
                        onExtractAudio = onExtractAudio,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardActions(
    item: LibraryItem,
    onPrimary: () -> Unit,
    onDelete: () -> Unit,
    primaryLabel: String,
    shareLabel: String,
    playable: Boolean,
    onExtractAudio: () -> Unit = {},
) {
    val context = LocalContext.current
    if (item.fileAvailable) {
        IconButton(onClick = onPrimary) {
            Icon(
                imageVector = if (playable) Icons.Rounded.PlayArrow else Icons.Rounded.OpenInBrowser,
                contentDescription = primaryLabel,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = { shareFile(context, item) }) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = shareLabel,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (mediaKindOf(item.fileUri) == MediaKind.VIDEO) {
            IconButton(onClick = onExtractAudio) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = stringResource(R.string.library_extract_audio),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    IconButton(onClick = onDelete) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = stringResource(R.string.library_delete),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Grid card: a large thumbnail on top, the title and size below, and an evenly
 * distributed action row. Unavailable items show only a Delete action (no disabled
 * play/share icons) so the row stays aligned and deletion is always easy to reach.
 */
@Composable
private fun LibraryGridCard(
    item: LibraryItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    flat: Boolean = false,
) {
    val tokens = LocalPdTokens.current
    val context = LocalContext.current
    val playLabel = stringResource(R.string.library_play, item.title)
    val openLabel = stringResource(R.string.library_open, item.title)
    val shareLabel = stringResource(R.string.library_share)
    val playable = mediaKindOf(item.fileUri).playable
    val onPrimary: () -> Unit = if (playable) onPlay else ({ openFile(context, item) })
    PdCard(
        onClick = if (item.fileAvailable) onPrimary else null,
        modifier = modifier,
        flat = flat,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            ThumbnailImage(
                uri = item.thumbnailUri,
                fallback = pdLibraryIcon(item.fileUri),
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(tokens.radius.md))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            if (item.fileAvailable) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = buildString {
                            item.sizeBytes?.let { append(formatBytes(it)) }
                            item.resolution?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                // No size/resolution for a missing file: show Unavailable centered instead.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PdStatusBadge(
                        label = stringResource(R.string.library_unavailable),
                        icon = Icons.Rounded.ErrorOutline,
                        tone = PdStatusTone.Error,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                if (item.fileAvailable) {
                        IconButton(onClick = onPrimary, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (playable) Icons.Rounded.PlayArrow else Icons.Rounded.OpenInBrowser,
                                contentDescription = if (playable) playLabel else openLabel,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = { shareFile(context, item) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = shareLabel,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.library_delete),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                } else {
                    // Full-width so the Delete stays a large, easy tap target in a narrow
                    // grid column; the row stays 48dp so all cards keep the same height.
                    FilledIconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.library_delete),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
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
    val shareUri = item.toShareableUri(context) ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(shareUri) ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.library_share_title)))
    }
}

/** Open a non-playable file (PDF, book, image, …) in an external app via ACTION_VIEW. */
private fun openFile(context: android.content.Context, item: LibraryItem) {
    val viewUri = item.toShareableUri(context) ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                viewUri,
                context.contentResolver.getType(viewUri) ?: "application/octet-stream",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

/** Resolve a library item's file into a shareable/viewable content URI, or null. */
private fun LibraryItem.toShareableUri(context: android.content.Context): Uri? {
    val source = Uri.parse(fileUri)
    return when (source.scheme) {
        "file" -> {
            val file = source.path?.let(::File) ?: return null
            if (!file.isFile) return null
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }
        "content" -> source
        else -> null
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