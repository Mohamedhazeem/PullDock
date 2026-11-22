package com.pulldock.feature.downloaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadState
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
                Text(text = details.task.displayName, style = MaterialTheme.typography.headlineSmall)
                PdProgress(
                    fraction = details.task.progress.percent?.toFloat(),
                    summary = stateText(details.task.state),
                )

                state.actionError?.let { error ->
                    PdErrorState(
                        title = stringResource(R.string.detail_action_failed_title),
                        message = stringResource(R.string.detail_action_failed_body),
                    )
                }

                details.torrent?.let { torrent ->
                    TorrentSection(torrent)
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

@Composable
private fun TorrentSection(torrent: TorrentTaskDetails) {
    val tokens = LocalPdTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text(stringResource(R.string.detail_torrent_section), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.detail_torrent_files, torrent.files.size, torrent.files.count { it.selected }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.detail_torrent_seeding, torrent.seedingPolicy.stopRatio ?: 0.0),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
        when (state) {
            DownloadState.RUNNING -> TextButton(onClick = onPause) { Text(stringResource(R.string.detail_action_pause)) }
            DownloadState.PAUSED -> TextButton(onClick = onResume) { Text(stringResource(R.string.detail_action_resume)) }
            DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED -> TextButton(onClick = onRetry) { Text(stringResource(R.string.detail_action_retry)) }
            else -> Unit
        }
        if (state != DownloadState.COMPLETED && state != DownloadState.CANCELLED && state != DownloadState.REMOVING) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.detail_action_cancel)) }
        }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.detail_action_remove)) }
    }
}

@Composable
private fun PdLoadingCompat() {
    com.pulldock.core.designsystem.components.PdLoadingState(label = stringResource(R.string.detail_loading))
}

@Composable
private fun stateText(state: DownloadState): String = state.name.lowercase().replace('_', ' ')
