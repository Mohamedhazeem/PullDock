package com.pulldock.feature.downloaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.core.designsystem.components.PdLoadingState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdStatData
import com.pulldock.core.designsystem.components.PdStatGrid
import com.pulldock.core.designsystem.components.PdStatusBadge
import com.pulldock.core.designsystem.components.pdStatusIcon
import com.pulldock.core.designsystem.components.pdStatusLabel
import com.pulldock.core.designsystem.components.pdStatusTone
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.formatBytes
import com.pulldock.core.model.formatSpeed
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.torrent.model.TorrentTaskDetails

/**
 * Download detail: state, progress, error, and per-task actions for either engine type.
 * Torrent-specific sections render when engine details exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: DownloadDetailViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(state.missing) {
        if (state.missing && !state.loading) onNavigateBack()
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { viewModel.consumeActionError() }
    }

    PdScaffold(
        title = stringResource(R.string.detail_title),
        onBack = onNavigateBack,
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        when {
            state.loading -> PdLoadingState(
                label = stringResource(R.string.detail_loading),
                modifier = Modifier.padding(innerPadding),
            )
            state.missing -> PdEmptyState(
                title = stringResource(R.string.detail_missing_title),
                message = stringResource(R.string.detail_missing_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> state.details?.let { details ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(horizontal = tokens.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                ) {
                    item {
                        ProgressSection(task = details.task, torrent = details.torrent)
                    }
                    state.actionError?.let {
                        item {
                            PdErrorState(
                                title = stringResource(R.string.detail_action_failed_title),
                                message = stringResource(R.string.detail_action_failed_body),
                            )
                        }
                    }
                    details.torrent?.let { torrent ->
                        item {
                            TorrentFileSelectionSection(
                                details = torrent,
                                onApply = { selection ->
                                    viewModel.applyFileSelection(details.task.id, selection)
                                },
                            )
                        }
                        item {
                            SeedingControlsSection(
                                details = torrent,
                                onApply = { policy ->
                                    viewModel.setSeedingPolicy(details.task.id, policy)
                                },
                            )
                        }
                    }
                    item {
                        ActionsRow(
                            state = details.task.state,
                            onPause = { viewModel.pause(details.task.id) },
                            onResume = { viewModel.resume(details.task.id) },
                            onRetry = { viewModel.retry(details.task.id) },
                            onCancel = { viewModel.cancel(details.task.id) },
                            onRemove = { confirmRemoval = true },
                        )
                    }
                }
            }
        }
    }

    if (confirmRemoval) {
        PdConfirmDialog(
            title = stringResource(R.string.detail_remove_title),
            text = stringResource(R.string.detail_remove_message),
            confirmLabel = stringResource(R.string.detail_remove_confirm),
            dismissLabel = stringResource(R.string.detail_remove_dismiss),
            onConfirm = {
                confirmRemoval = false
                viewModel.remove(viewModel.taskId, deleteFile = false, onDone = onNavigateBack)
            },
            onDismiss = { confirmRemoval = false },
        )
    }
}

/** Hero progress card: name, state, bar, and compact metrics. */
@Composable
private fun ProgressSection(
    task: DownloadTask,
    torrent: TorrentTaskDetails?,
) {
    val tokens = LocalPdTokens.current
    val stateDesc = pdStatusLabel(task.state)
    val eta = task.progress.etaSeconds
    PdCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = stateDesc },
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(text = task.displayName, style = MaterialTheme.typography.headlineSmall)
            PdStatusBadge(
                label = stateDesc,
                icon = pdStatusIcon(task.state),
                tone = pdStatusTone(task.state),
            )
            PdProgress(
                fraction = task.progress.percent?.toFloat(),
                summary = buildString {
                    append(
                        task.progress.totalBytes?.let { total ->
                            "${formatBytes(task.progress.downloadedBytes)} / ${formatBytes(total)}"
                        } ?: formatBytes(task.progress.downloadedBytes),
                    )
                    if (task.state == DownloadState.RUNNING && task.progress.speedBytesPerSec > 0) {
                        append(" · ")
                        append(formatSpeed(task.progress.speedBytesPerSec.toDouble()))
                    }
                    eta?.let {
                        append(" · ")
                        append(stringResource(R.string.detail_eta, formatSeconds(it)))
                    }
                    torrent?.let {
                        if (it.peerCount > 0 || it.seedCount > 0) {
                            append(" · ${it.peerCount} peers / ${it.seedCount} seeds")
                        }
                    }
                },
            )
            PdStatGrid(
                stats = listOf(
                    PdStatData(
                        label = stringResource(R.string.detail_stat_size),
                        value = task.progress.totalBytes?.let(::formatBytes)
                            ?: formatBytes(task.progress.downloadedBytes),
                    ),
                    PdStatData(
                        label = stringResource(R.string.detail_stat_speed),
                        value = if (task.progress.speedBytesPerSec > 0) {
                            formatSpeed(task.progress.speedBytesPerSec.toDouble())
                        } else {
                            "—"
                        },
                    ),
                    PdStatData(
                        label = stringResource(R.string.detail_stat_eta),
                        value = eta?.let(::formatSeconds) ?: "—",
                    ),
                    PdStatData(
                        label = stringResource(R.string.detail_stat_peers),
                        value = "${torrent?.peerCount ?: 0}",
                    ),
                ),
            )
        }
    }
}

/** Formats a duration compactly ("3m 12s" / "45s" / "2h 5m"). */
private fun formatSeconds(seconds: Long): String {
    val abs = seconds.coerceAtLeast(0)
    return when {
        abs < 60 -> "%d s".format(abs)
        abs < 3600 -> "%d m %d s".format(abs / 60, abs % 60)
        else -> "%d h %d m".format(abs / 3600, (abs % 3600) / 60)
    }
}

/** Labeled actions with a clear primary state. */
@Composable
private fun ActionsRow(
    state: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            Text(
                text = stringResource(R.string.detail_actions_title),
                style = MaterialTheme.typography.titleMedium,
            )
            when (state) {
                DownloadState.RUNNING -> ActionButton(
                    Icons.Rounded.Pause,
                    stringResource(R.string.detail_action_pause),
                    onPause,
                    primary = true,
                )
                DownloadState.PAUSED -> ActionButton(
                    Icons.Rounded.PlayArrow,
                    stringResource(R.string.detail_action_resume),
                    onResume,
                    primary = true,
                )
                DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED,
                DownloadState.NEEDS_PERMISSION -> ActionButton(
                    Icons.Rounded.Autorenew,
                    stringResource(R.string.detail_action_retry),
                    onRetry,
                    primary = true,
                )
                else -> Unit
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                if (state != DownloadState.COMPLETED && state != DownloadState.CANCELLED && state != DownloadState.REMOVING) {
                    ActionButton(
                        Icons.Rounded.Close,
                        stringResource(R.string.detail_action_cancel),
                        onCancel,
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                ActionButton(
                    Icons.Rounded.Delete,
                    stringResource(R.string.detail_action_remove),
                    onRemove,
                    primary = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
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