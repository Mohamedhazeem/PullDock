package com.pulldock.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdButton
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdFilterChips
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdSkeleton
import com.pulldock.core.designsystem.components.PdStatData
import com.pulldock.core.designsystem.components.PdStatGrid
import com.pulldock.core.designsystem.components.PdStatusBadge
import com.pulldock.core.designsystem.components.pdStatusIcon
import com.pulldock.core.designsystem.components.pdStatusLabel
import com.pulldock.core.designsystem.components.pdStatusTone
import com.pulldock.core.designsystem.components.pdTypeIcon
import com.pulldock.core.designsystem.components.pdTypeLabel
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.formatBytes
import com.pulldock.core.model.formatSpeed
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.torrent.model.TorrentTaskDetails

/** Unified transfer screen: calm overview, filters, and per-task controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddDownload: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val actionMessage = state.actionError?.let { stringResource(R.string.home_error_action) }
    val filterOptions = QueueFilter.entries.map { filterLabel(it) }

    LaunchedEffect(state.actionError) {
        actionMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeActionError()
        }
    }

    PdScaffold(
        title = stringResource(R.string.home_title),
        subtitle = stringResource(R.string.home_subtitle),
        snackbarHostState = snackbar,
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDownload,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            item {
                HomeHero(
                    tasks = state.tasks,
                    onAddDownload = onAddDownload,
                )
            }
            item {
                PdFilterChips(
                    options = filterOptions,
                    selected = filterLabel(state.filter),
                    onSelect = { selected ->
                        val index = filterOptions.indexOf(selected)
                        viewModel.setFilter(
                            QueueFilter.entries.getOrNull(index) ?: state.filter,
                        )
                    },
                )
            }

            if (state.loading) {
                items(3) { LoadingTransferCard() }
            } else if (state.tasks.isEmpty()) {
                item {
                    PdEmptyState(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_body),
                        icon = Icons.Rounded.CloudDownload,
                        actionLabel = stringResource(R.string.home_add),
                        onAction = onAddDownload,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                items(
                    items = state.tasks,
                    key = { it.id },
                    contentType = { "transfer" },
                ) { task ->
                    TransferCard(
                        task = task,
                        details = state.torrentDetails[task.id],
                        onOpen = { onOpenTask(task.id) },
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onRetry = { viewModel.retry(task.id) },
                        onDelete = { viewModel.requestDelete(task.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    state.pendingDelete?.let {
        PdConfirmDialog(
            title = stringResource(R.string.home_delete_title),
            text = stringResource(R.string.home_delete_body),
            confirmLabel = stringResource(R.string.home_delete_confirm),
            dismissLabel = stringResource(R.string.home_delete_dismiss),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun HomeHero(
    tasks: List<DownloadTask>,
    onAddDownload: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    val active = tasks.count { it.state in ACTIVE_STATES }
    val queued = tasks.count { it.state == DownloadState.QUEUED || it.state == DownloadState.RETRY_WAIT }
    val completed = tasks.count { it.state == DownloadState.COMPLETED }

    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(
                    text = stringResource(R.string.home_hero_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_hero_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PdStatGrid(
                stats = listOf(
                    PdStatData(
                        label = stringResource(R.string.home_active_label),
                        value = active.toString(),
                        icon = Icons.Rounded.CloudDownload,
                    ),
                    PdStatData(
                        label = stringResource(R.string.home_queued_label),
                        value = queued.toString(),
                        icon = Icons.Rounded.Schedule,
                    ),
                    PdStatData(
                        label = stringResource(R.string.home_completed_label),
                        value = completed.toString(),
                        icon = Icons.Rounded.CheckCircle,
                    ),
                ),
            )
            PdButton(
                label = stringResource(R.string.home_add),
                onClick = onAddDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TransferCard(
    task: DownloadTask,
    details: TorrentTaskDetails?,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    val stateLabel = pdStatusLabel(task.state)
    val sizeText = task.progress.totalBytes?.let { total ->
        if (total > 0) "${formatBytes(task.progress.downloadedBytes)} / ${formatBytes(total)}"
        else formatBytes(task.progress.downloadedBytes)
    } ?: formatBytes(task.progress.downloadedBytes)

    PdCard(
        onClick = onOpen,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(tokens.radius.md))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = pdTypeIcon(task.type),
                        contentDescription = pdTypeLabel(task.type),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(tokens.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = "${pdTypeLabel(task.type)} · $stateLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OverflowMenu(onDelete = onDelete)
            }

            PdStatusBadge(
                label = stateLabel,
                icon = pdStatusIcon(task.state),
                tone = pdStatusTone(task.state),
            )
            PdProgress(fraction = task.progress.percent?.toFloat(), summary = sizeText)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                StatSlot(
                    "↓ " + if (task.progress.speedBytesPerSec > 0) {
                        formatSpeed(task.progress.speedBytesPerSec.toDouble())
                    } else {
                        "—"
                    },
                    Modifier.weight(1f),
                )
                StatSlot(
                    "↑ " + if ((task.uploadSpeed ?: 0L) > 0) {
                        formatSpeed((task.uploadSpeed ?: 0L).toDouble())
                    } else {
                        "—"
                    },
                    Modifier.weight(1f),
                )
                StatSlot("${details?.peerCount ?: 0} peers", Modifier.weight(1f))
                StatSlot("${details?.seedCount ?: 0} seeds", Modifier.weight(1f))
            }

            when (task.state) {
                DownloadState.RUNNING -> ActionButton(
                    Icons.Rounded.Pause,
                    stringResource(R.string.home_action_pause),
                    onPause,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DownloadState.PAUSED -> ActionButton(
                    Icons.Rounded.PlayArrow,
                    stringResource(R.string.home_action_resume),
                    onResume,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED,
                DownloadState.NEEDS_PERMISSION -> ActionButton(
                    Icons.Rounded.PlayArrow,
                    stringResource(R.string.home_action_retry),
                    onRetry,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun OverflowMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.home_more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_action_delete)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun StatSlot(value: String, modifier: Modifier = Modifier) {
    Text(
        value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier.height(24.dp),
    )
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(LocalPdTokens.current.spacing.xs))
        Text(label)
    }
    val height = Modifier.heightIn(min = 48.dp)
    if (primary) {
        Button(onClick = onClick, modifier = modifier.then(height), content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.then(height), content = content)
    }
}

@Composable
private fun LoadingTransferCard() {
    val tokens = LocalPdTokens.current
    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PdSkeleton(modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(tokens.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    PdSkeleton(modifier = Modifier.fillMaxWidth(0.7f))
                    Spacer(Modifier.height(tokens.spacing.xs))
                    PdSkeleton(modifier = Modifier.fillMaxWidth(0.4f))
                }
            }
            PdSkeleton(modifier = Modifier.fillMaxWidth())
            PdSkeleton(modifier = Modifier.fillMaxWidth(0.8f))
        }
    }
}

@Composable
private fun filterLabel(filter: QueueFilter): String = when (filter) {
    QueueFilter.ALL -> stringResource(R.string.home_filter_all)
    QueueFilter.ACTIVE -> stringResource(R.string.home_filter_active)
    QueueFilter.QUEUED -> stringResource(R.string.home_filter_queued)
    QueueFilter.PAUSED -> stringResource(R.string.home_filter_paused)
    QueueFilter.COMPLETED -> stringResource(R.string.home_filter_completed)
    QueueFilter.FAILED -> stringResource(R.string.home_filter_failed)
}

private val ACTIVE_STATES = setOf(
    DownloadState.STARTING,
    DownloadState.RUNNING,
    DownloadState.PAUSING,
    DownloadState.VERIFYING,
    DownloadState.POST_PROCESSING,
)