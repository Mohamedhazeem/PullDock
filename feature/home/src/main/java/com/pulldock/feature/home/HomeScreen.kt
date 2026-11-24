package com.pulldock.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.formatBytes
import com.pulldock.core.model.formatSpeed
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.torrent.model.TorrentTaskDetails

/** Single unified transfer screen (FR-023): filters + pause/resume/delete only. */
@Composable
fun HomeScreen(
    onAddDownload: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val actionMessage = state.actionError?.let { stringResource(R.string.home_error_action) }
    LaunchedEffect(state.actionError) {
        actionMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeActionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val addDescription = stringResource(R.string.home_add_description)
            ExtendedFloatingActionButton(
                onClick = onAddDownload,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_add)) },
                modifier = Modifier.semantics { contentDescription = addDescription },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                // Menu and its anchor share a Box so the popup opens directly under the icon.
                Box {
                    val filterDescription = stringResource(R.string.home_filter_button)
                    IconButton(
                        onClick = { filterMenuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = filterDescription },
                    ) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = filterDescription,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                        offset = androidx.compose.ui.unit.DpOffset(x = 200.dp, y = 4.dp),
                        modifier = Modifier.width(180.dp),
                    ) {
                        QueueFilter.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        filterLabel(option),
                                        color = if (option == state.filter) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    viewModel.setFilter(option)
                                    filterMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                state.tasks.isEmpty() -> PdEmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_body),
                    icon = Icons.Filled.CloudDownload,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                ) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            details = state.torrentDetails[task.id],
                            onOpen = { onOpenTask(task.id) },
                            onPause = { viewModel.pause(task.id) },
                            onResume = { viewModel.resume(task.id) },
                            onRetry = { viewModel.retry(task.id) },
                            onDelete = { viewModel.requestDelete(task.id) },
                        )
                    }
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
private fun TaskCard(
    task: DownloadTask,
    details: TorrentTaskDetails?,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    val stateLabel = stateLabel(task.state)
    val sizeText = task.progress.totalBytes?.let { total ->
        if (total > 0) "${formatBytes(task.progress.downloadedBytes)} / ${formatBytes(total)}"
        else formatBytes(task.progress.downloadedBytes)
    } ?: formatBytes(task.progress.downloadedBytes)

    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.radius.lg),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(tokens.radius.md))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(tokens.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        text = "${task.type.name.lowercase().replaceFirstChar { it.uppercase() }} · $stateLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Progress summary shows size, not a repeated state label.
            PdProgress(fraction = task.progress.percent?.toFloat(), summary = sizeText)

            Row(
                modifier = Modifier.fillMaxWidth().height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                val upload = task.uploadSpeed ?: 0L
                StatSlot(
                    "↓ " + if (task.progress.speedBytesPerSec > 0) formatSpeed(task.progress.speedBytesPerSec.toDouble()) else "—",
                    Modifier.weight(1f),
                )
                StatSlot("↑ " + if (upload > 0) formatSpeed(upload.toDouble()) else "—", Modifier.weight(1f))
                StatSlot("${details?.peerCount ?: 0} peers", Modifier.weight(1f))
                StatSlot("${details?.seedCount ?: 0} seeds", Modifier.weight(1f))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                when (task.state) {
                    DownloadState.RUNNING -> ActionButton(
                        Icons.Filled.Pause,
                        stringResource(R.string.home_action_pause),
                        onPause,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    DownloadState.PAUSED -> ActionButton(
                        Icons.Filled.PlayArrow,
                        stringResource(R.string.home_action_resume),
                        onResume,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED,
                    DownloadState.NEEDS_PERMISSION -> ActionButton(
                        Icons.Filled.PlayArrow,
                        stringResource(R.string.home_action_resume),
                        onRetry,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    else -> Unit
                }
                ActionButton(
                    Icons.Filled.Delete,
                    stringResource(R.string.home_action_delete),
                    onDelete,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
            }
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
    val tokens = LocalPdTokens.current
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(tokens.spacing.xs))
        Text(label)
    }
    val height = Modifier.heightIn(min = 48.dp)
    if (primary) {
        androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = modifier.then(height), content = content)
    } else {
        androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier.then(height), content = content)
    }
}

@Composable
private fun stateLabel(state: DownloadState): String = state.name.lowercase().replace('_', ' ')

@Composable
private fun filterLabel(filter: QueueFilter): String = when (filter) {
    QueueFilter.ALL -> stringResource(R.string.home_filter_all)
    QueueFilter.ACTIVE -> stringResource(R.string.home_filter_active)
    QueueFilter.QUEUED -> stringResource(R.string.home_filter_queued)
    QueueFilter.PAUSED -> stringResource(R.string.home_filter_paused)
    QueueFilter.COMPLETED -> stringResource(R.string.home_filter_completed)
    QueueFilter.FAILED -> stringResource(R.string.home_filter_failed)
}
