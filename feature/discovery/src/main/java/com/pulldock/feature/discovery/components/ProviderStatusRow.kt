package com.pulldock.feature.discovery.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.ProviderState
import com.pulldock.domain.discovery.model.ProviderStatus

/**
 * Per-provider availability chips (FR-040). Tapping a chip toggles that provider as a filter
 * (tap again to clear); the selection updates the search and the filter sheet.
 */
@Composable
fun ProviderStatusRow(
    statuses: List<ProviderStatus>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        statuses.forEach { status ->
            val label = when (status.state) {
                ProviderState.SUCCESS -> "${status.providerName}: ${status.resultCount ?: 0}"
                ProviderState.EMPTY -> "${status.providerName}: no results"
                ProviderState.RATE_LIMITED -> "${status.providerName}: rate limited"
                ProviderState.TIMEOUT -> "${status.providerName}: timed out"
                ProviderState.FAILED -> "${status.providerName}: failed"
                ProviderState.DISABLED -> "${status.providerName}: disabled"
            }
            FilterChip(
                selected = status.providerId in selectedIds,
                onClick = { onToggle(status.providerId) },
                label = { Text(label) },
                leadingIcon = if (status.providerId in selectedIds) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
            )
        }
    }
}