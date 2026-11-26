package com.pulldock.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens

/** One stacked bar: Wi-Fi bytes (primary) + mobile bytes (tertiary) for a day. */
data class PdUsageBar(
    val label: String,
    val wifiBytes: Long,
    val mobileBytes: Long,
)

/**
 * Dependency-free stacked bar chart for daily usage. Renders an empty state when there is no
 * data; [contentDescription] provides a TalkBack summary; [emptyLabel] is shown when empty.
 */
@Composable
fun PdBarChart(
    bars: List<PdUsageBar>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    emptyLabel: String? = null,
) {
    val tokens = LocalPdTokens.current
    val maxTotal = bars.maxOfOrNull { it.wifiBytes + it.mobileBytes } ?: 0L
    if (bars.isEmpty() || maxTotal == 0L) {
        Box(
            modifier = modifier.height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (emptyLabel != null) {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val wifiColor = MaterialTheme.colorScheme.primary
    val mobileColor = MaterialTheme.colorScheme.tertiary
    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        ) {
            val n = bars.size
            val slot = size.width / n
            val barWidth = slot * 0.6f
            val maxF = maxTotal.toFloat()
            bars.forEachIndexed { index, bar ->
                val total = (bar.wifiBytes + bar.mobileBytes).toFloat()
                val height = (total / maxF) * size.height
                val left = index * slot + (slot - barWidth) / 2f
                val wifiHeight = if (total > 0f) height * (bar.wifiBytes.toFloat() / total) else 0f
                val mobileHeight = height - wifiHeight
                // Mobile at the bottom, Wi-Fi stacked on top.
                if (mobileHeight > 0f) {
                    drawRect(
                        color = mobileColor,
                        topLeft = Offset(left, size.height - mobileHeight),
                        size = Size(barWidth, mobileHeight),
                    )
                }
                if (wifiHeight > 0f) {
                    drawRect(
                        color = wifiColor,
                        topLeft = Offset(left, size.height - mobileHeight - wifiHeight),
                        size = Size(barWidth, wifiHeight),
                    )
                }
            }
        }
        if (bars.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.xs),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(bars.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bars.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}