package com.pulldock.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.download.model.DownloadTask

/**
 * Home: quick add entry, active-download summary, and the most recent tasks (T043).
 * All numbers come from the live queue — nothing is faked while the queue is empty.
 */
@Composable
fun HomeScreen(
    onAddDownload: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
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
                .padding(horizontal = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = tokens.spacing.md),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
                SummaryCard(
                    label = stringResource(R.string.home_active_label),
                    value = state.activeCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    label = stringResource(R.string.home_queued_label),
                    value = state.queuedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }

            if (!state.loading && state.recent.isEmpty()) {
                PdEmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = tokens.spacing.xl),
                ) {
                    items(state.recent, key = { it.id }) { task ->
                        RecentTaskCard(task = task, onClick = { onOpenTask(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(LocalPdTokens.current.spacing.md),
            verticalArrangement = Arrangement.spacedBy(LocalPdTokens.current.spacing.xs),
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

@Composable
private fun RecentTaskCard(task: DownloadTask, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LocalPdTokens.current.spacing.md),
            verticalArrangement = Arrangement.spacedBy(LocalPdTokens.current.spacing.xs),
        ) {
            Text(text = task.displayName, style = MaterialTheme.typography.titleMedium)
            PdProgress(
                fraction = task.progress.percent?.toFloat(),
                summary = task.state.name.lowercase().replace('_', ' '),
            )
        }
    }
}
