package com.pulldock.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens

/** One compact metric for summary and detail headers. */
data class PdStatData(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
)

@Composable
fun PdStat(
    stat: PdStatData,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    PdCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            if (stat.icon != null) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stat.value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stat.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two-column stat grid that keeps cards readable on wide screens. */
@Composable
fun PdStatGrid(
    stats: List<PdStatData>,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        stats.chunked(2).forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                rowStats.forEach { stat ->
                    PdStat(stat = stat, modifier = Modifier.weight(1f))
                }
                if (rowStats.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}