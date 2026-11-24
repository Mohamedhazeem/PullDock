package com.pulldock.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.formatBytes
import com.pulldock.core.model.formatSpeed
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.torrent.model.TorrentTaskDetails

/**
 * Home: modern dashboard with inline per-task controls (FR-007), live torrent telemetry
 * (FR-044/US4), and honest empty/loading states (FR-067). All numbers come from the
 * live queue — nothing is faked.
 */
@Composable
fun HomeScreen(
    onAddDownload: () -> Unit,
    onOpenTask: (String) -> Unit,
    onPlayItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
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
                .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
        ) {
            val titleDescription = stringResource(R.string.home_title)
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { contentDescription = titleDescription },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
                SummaryCard(
                    label = stringResource(R.string.home_active_label),
                    value = state.activeCount.toString(),
                    icon = Icons.Filled.CloudDownload,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    label = stringResource(R.string.home_queued_label),
                    value = state.queuedCount.toString(),
                    icon = Icons.Filled.Download,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.loading) {
                val loadingDesc = stringResource(R.string.home_loading)
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = loadingDesc },
                )
            } else if (state.recent.isEmpty()) {
                PdEmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_body),
                    icon = Icons.Filled.CloudDownload,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                ) {
                    items(state.recent, key = { it.id }) { task ->
                        RecentTaskCard(
                            task = task,
                            details = state.torrentDetails[task.id],
                            onOpen = { onOpenTask(task.id) },
                            onPause = { viewModel.pause(task.id) },
                            onResume = { viewModel.resume(task.id) },
                            onRetry = { viewModel.retry(task.id) },
                            onCancel = { viewModel.cancel(task.id) },
                            onRemove = { viewModel.requestRemove(task.id) },
                        )
                    }
                }
            }

            if (state.completed.isNotEmpty()) {
                Text(
                    stringResource(R.string.home_completed_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
                    items(state.completed, key = { it.id }) { item ->
                        CompletedCard(item = item, onPlay = { onPlayItem(item.id) })
                    }
                }
            }
        }
    }

    state.pendingRemoval?.let { id ->
        PdConfirmDialog(
            title = stringResource(R.string.home_remove_title),
            text = stringResource(R.string.home_remove_body),
            confirmLabel = stringResource(R.string.home_remove_confirm),
            dismissLabel = stringResource(R.string.home_remove_dismiss),
            onConfirm = { viewModel.confirmRemove(deleteFile = false) },
            onDismiss = viewModel::dismissRemove,
        )
    }
}

/** Compact summary card: value + icon, label beneath. */
@Composable
private fun SummaryCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = tokens.spacing.md),
            ) {
                Text(text = value, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Elevated task card with live progress, telemetry, and inline controls (FR-007/FR-044). */
@Composable
private fun RecentTaskCard(
    task: DownloadTask,
    details: TorrentTaskDetails?,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    val stateLabel = task.state.name.lowercase().replace('_', ' ')
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = task.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(text = stateLabel, style = MaterialTheme.typography.bodySmall)
            }
            PdProgress(fraction = task.progress.percent?.toFloat(), summary = stateLabel)
            // Fixed slots keep the card height stable as speeds fluctuate (no layout jump).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                StatSlot("↓ " + if (task.progress.speedBytesPerSec > 0) formatSpeed(task.progress.speedBytesPerSec.toDouble()) else "—")
                StatSlot("↑ " + if ((task.uploadSpeed ?: 0L) > 0) formatSpeed(task.uploadSpeed!!.toDouble()) else "—")
                StatSlot("${details?.peerCount ?: 0} peers")
                StatSlot("${details?.seedCount ?: 0} seeds")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                when (task.state) {
                    DownloadState.RUNNING -> CardAction(Icons.Filled.Pause, stringResource(R.string.home_action_pause), onPause)
                    DownloadState.PAUSED -> CardAction(Icons.Filled.PlayArrow, stringResource(R.string.home_action_resume), onResume)
                    DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED ->
                        CardAction(Icons.Filled.Replay, stringResource(R.string.home_action_retry), onRetry)
                    else -> Unit
                }
                if (task.state !in setOf(
                        DownloadState.COMPLETED,
                        DownloadState.CANCELLED,
                        DownloadState.REMOVING,
                        DownloadState.FAILED,
                        DownloadState.NEEDS_USER_ACTION,
                    )
                ) {
                    CardAction(Icons.Filled.Close, stringResource(R.string.home_action_cancel), onCancel)
                }
                CardAction(Icons.Filled.Delete, stringResource(R.string.home_action_remove), onRemove)
            }
        }
    }
}

/** Fixed-width telemetry slot — no card resizing when numbers change. */
@Composable
private fun StatSlot(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .weight(1f)
            .height(24.dp),
    )
}

/** Completed item on the dashboard; opens the player directly. */
@Composable
private fun CompletedCard(item: LibraryItem, onPlay: () -> Unit) {
    val tokens = LocalPdTokens.current
    val playLabel = stringResource(R.string.home_play_item, item.title)
    Card(modifier = Modifier.width(220.dp)) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                text = item.sizeBytes?.let(::formatBytes) ?: stringResource(R.string.home_completed_ready),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.fileAvailable) {
                androidx.compose.material3.FilledTonalButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = playLabel)
                    Text(stringResource(R.string.home_play))
                }
            } else {
                Text(
                    stringResource(R.string.home_item_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = label },
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}
