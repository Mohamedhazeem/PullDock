package com.pulldock.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdConfirmDialog
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.QueueFilter

/**
 * Unified queue screen (T045, US4): both engine types with filters and per-task
 * controls. At ≥720dp widths the list shows a list-detail pane with the selected task
 * (adaptive layout, T052); on compact widths tapping a task opens the detail route.
 * Removal goes through a confirmation dialog (FR-041); per-task actions stay
 * independent of unrelated tasks (SC-005).
 */
@Composable
fun QueueScreen(
    onOpenTask: (String) -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    // Resolve the message in composable scope (stringResource); the effect only shows it.
    val actionMessage = state.actionError?.let { actionErrorText(it) }
    LaunchedEffect(state.actionError) {
        actionMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeActionError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val expanded = maxWidth >= 720.dp
            if (expanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {
                        QueueContent(state, viewModel, onOpenTask = { selectedId = it })
                    }
                    val selected = state.tasks.firstOrNull { it.id == selectedId }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = tokens.spacing.md),
                    ) {
                        if (selected == null) {
                            PdEmptyState(
                                title = stringResource(R.string.queue_detail_none_title),
                                message = stringResource(R.string.queue_detail_none_body),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            DetailPane(selected)
                        }
                    }
                }
            } else {
                QueueContent(state, viewModel, onOpenTask)
            }
        }
    }

    if (state.showingRemovalConfirmation) {
        PdConfirmDialog(
            title = stringResource(R.string.queue_remove_title),
            text = stringResource(R.string.queue_remove_message),
            confirmLabel = stringResource(R.string.queue_remove_confirm),
            dismissLabel = stringResource(R.string.queue_remove_dismiss),
            onConfirm = { viewModel.confirmRemove(deleteFile = false) },
            onDismiss = viewModel::dismissRemove,
        )
    }
}

@Composable
private fun DetailPane(task: DownloadTask) {
    val tokens = LocalPdTokens.current
    val stateText = task.state.name.lowercase().replace('_', ' ')
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Text(text = task.displayName, style = MaterialTheme.typography.titleLarge)
        PdProgress(fraction = task.progress.percent?.toFloat(), summary = stateText)
        Text(
            text = task.source,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QueueContent(state: QueueUiState, viewModel: QueueViewModel, onOpenTask: (String) -> Unit) {
    val tokens = LocalPdTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tokens.spacing.md),
    ) {
        Text(
            text = stringResource(R.string.queue_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = tokens.spacing.md),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            items(QueueFilter.entries) { filter ->
                FilterChip(
                    selected = filter == state.filter,
                    onClick = { viewModel.onFilterChanged(filter) },
                    label = { Text(filterLabel(filter)) },
                )
            }
        }

        if (!state.loading && state.tasks.isEmpty()) {
            PdEmptyState(
                title = stringResource(R.string.queue_empty_title),
                message = stringResource(R.string.queue_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                contentPadding = PaddingValues(bottom = tokens.spacing.lg),
            ) {
                items(state.tasks, key = { it.id }) { task ->
                    QueueItem(
                        task = task,
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onRetry = { viewModel.retry(task.id) },
                        onCancel = { viewModel.cancel(task.id) },
                        onRemove = { viewModel.requestRemove(task.id) },
                        onOpen = { onOpenTask(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    val stateText = task.state.name.lowercase().replace('_', ' ')
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = stateText },
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            Text(text = task.displayName, style = MaterialTheme.typography.titleMedium)
            PdProgress(fraction = task.progress.percent?.toFloat(), summary = stateText)
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                when (task.state) {
                    DownloadState.RUNNING -> TextButton(onClick = onPause) { Text(stringResource(R.string.queue_action_pause)) }
                    DownloadState.PAUSED -> TextButton(onClick = onResume) { Text(stringResource(R.string.queue_action_resume)) }
                    DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION, DownloadState.BLOCKED -> TextButton(onClick = onRetry) { Text(stringResource(R.string.queue_action_retry)) }
                    else -> Unit
                }
                if (task.state in CancelableStates) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.queue_action_cancel)) }
                }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.queue_action_remove)) }
            }
        }
    }
}

private val CancelableStates = setOf(
    DownloadState.CREATED,
    DownloadState.VALIDATING,
    DownloadState.RESOLVING,
    DownloadState.FETCHING_METADATA,
    DownloadState.AWAITING_FILE_SELECTION,
    DownloadState.AWAITING_FORMAT_SELECTION,
    DownloadState.QUEUED,
    DownloadState.STARTING,
    DownloadState.RUNNING,
    DownloadState.PAUSING,
    DownloadState.PAUSED,
    DownloadState.RETRY_WAIT,
    DownloadState.VERIFYING,
    DownloadState.POST_PROCESSING,
)

@Composable
private fun filterLabel(filter: QueueFilter): String = when (filter) {
    QueueFilter.ALL -> stringResource(R.string.queue_filter_all)
    QueueFilter.ACTIVE -> stringResource(R.string.queue_filter_active)
    QueueFilter.QUEUED -> stringResource(R.string.queue_filter_queued)
    QueueFilter.PAUSED -> stringResource(R.string.queue_filter_paused)
    QueueFilter.COMPLETED -> stringResource(R.string.queue_filter_completed)
    QueueFilter.FAILED -> stringResource(R.string.queue_filter_failed)
}

@Composable
internal fun actionErrorText(@Suppress("UNUSED_PARAMETER") error: DownloadError): String =
    stringResource(R.string.queue_error_action)
