package com.pulldock.feature.discovery.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.ProviderState
import com.pulldock.domain.discovery.model.ProviderStatus

/** Per-provider availability/degraded status (FR-040, FR-068). */
@Composable
fun ProviderStatusRow(statuses: List<ProviderStatus>, modifier: Modifier = Modifier) {
    val tokens = LocalPdTokens.current
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = tokens.spacing.xs),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(tokens.spacing.sm),
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
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(tokens.spacing.xs))
            }
        }
    }
}