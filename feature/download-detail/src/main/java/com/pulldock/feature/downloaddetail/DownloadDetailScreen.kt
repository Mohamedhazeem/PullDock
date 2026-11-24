package com.pulldock.feature.downloaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.formatBytes
import com.pulldock.domain.torrent.model.TorrentTaskDetails

/**
 * Download detail screen (T046): state, progress, error, and per-task actions for
 * either engine type. Torrent-specific sections (files, seeding) render when the
 * engine details exist (expanded in Slice 3, T057/T067). Removal requires explicit
 * confirmation (FR-041); file deletion stays a separate choice (FR-027).
 */
@Composable
fun DownloadDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: DownloadDetailViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmRemoval by remember { mutableStateOf(false) }

    LaunchedEffect(state.missing) {
        if (state.missing && !state.loading) onNavigateBack()
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { viewModel.consumeActionError() } // surfaced inline via sections
    }

    when {
        state.loading -> PdLoadingCompat()
        state.missing -> PdEmptyState(
            title = stringResource(R.string.detail_missing_title),
            message = stringResource(R.string.detail_missing_body),
            modifier = Modifier.fillMaxSize(),
        )
        else -> state.details?.let { details ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                ProgressSection(task = details.task, torrent = details.torrent)

                state.actionError?.let { error ->
                    PdErrorState(
                        title = stringResource(R.string.detail_action_failed_title),
                        message = stringResource(R.string.detail_action_failed_body),
                    )
                }

                details.torrent?.let { torrent ->
                    TorrentFileSelectionSection(
                        details = torrent,
                        onApply = { selection -> viewModel.applyFileSelection(details.task.id, selection) },
                    )
                    SeedingControlsSection(
                        details = torrent,
                        onApply = { policy -> viewModel.setSeedingPolicy(details.task.id, policy) },
                    )
                }

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

/** Modern progress section: name, state, bar with size/speed/ETA. */
@Composable
private fun ProgressSection(
    task: com.pulldock.domain.download.model.DownloadTask,
    torrent: TorrentTaskDetails?,
) {
    val tokens = LocalPdTokens.current
    val stateDesc = stateText(task.state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = stateDesc },
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        Text(text = task.displayName, style = MaterialTheme.typography.headlineMedium)

        PdProgress(
            fraction = task.progress.percent?.toFloat(),
            summary = buildString {
                append(stateDesc)
                task.progress.totalBytes?.let { total ->
                    append(" · ")
                    append(formatBytes(task.progress.downloadedBytes))
                    append(" / ")
                    append(formatBytes(total))
                }
                if (task.state == DownloadState.RUNNING && task.progress.speedBytesPerSec > 0) {
                    append(" · ")
                    append(com.pulldock.core.model.formatSpeed(task.progress.speedBytesPerSec.toDouble()))
                }
                task.progress.etaSeconds?.let { eta ->
                    append(" · ")
                    append(stringResource(R.string.detail_eta, formatSeconds(eta)))
                }
                torrent?.let {
                    if (it.peerCount > 0 || it.seedCount > 0) {
                        append(" · ${it.peerCount} peers / ${it.seedCount} seeds")
                    }
                }
            },
        )
    }
}

/** Formats a duration compactly ("3m 12s" / "45s" / "2h 5m"). */
@Composable
private fun formatSeconds(seconds: Long): String {
    val abs = seconds.coerceAtLeast(0)
    return when {
        abs < 60 -> "%d s".format(abs)
        abs < 3600 -> "%d m %d s".format(abs / 60, abs % 60)
        else -> "%d h %d m".format(abs / 3600, (abs % 3600) / 60)
    }
}

/** Modern icon-button actions row. */
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        when (state) {
            DownloadState.RUNNING -> ActionButton(Icons.Filled.Pause, stringResource(R.string.detail_action_pause), onPause)
            DownloadState.PAUSED -> ActionButton(Icons.Filled.PlayArrow, stringResource(R.string.detail_action_resume), onResume)
            DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED ->
                ActionButton(Icons.Filled.Autorenew, stringResource(R.string.detail_action_retry), onRetry)
            else -> Unit
        }
        if (state != DownloadState.COMPLETED && state != DownloadState.CANCELLED && state != DownloadState.REMOVING) {
            ActionButton(Icons.Filled.Close, stringResource(R.string.detail_action_cancel), onCancel)
        }
        ActionButton(Icons.Filled.Delete, stringResource(R.string.detail_action_remove), onRemove)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .semantics { contentDescription = label }
            .size(48.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

@Composable
private fun PdLoadingCompat() {
    com.pulldock.core.designsystem.components.PdLoadingState(label = stringResource(R.string.detail_loading))
}

@Composable
private fun stateText(state: DownloadState): String = state.name.lowercase().replace('_', ' ')
