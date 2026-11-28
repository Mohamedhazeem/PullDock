package com.pulldock.feature.discovery.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.SearchHistoryEntry

/** Local recent searches with repeat and clear-all (FR-061). */
@Composable
fun SearchHistoryRow(
    history: List<SearchHistoryEntry>,
    onRepeat: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Row(
        modifier = modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text("Recent:", style = MaterialTheme.typography.labelSmall)
        history.take(8).forEach { entry ->
            AssistChip(onClick = { onRepeat(entry.query) }, label = { Text(entry.query) })
        }
        TextButton(onClick = onClear) { Text("Clear") }
    }
}