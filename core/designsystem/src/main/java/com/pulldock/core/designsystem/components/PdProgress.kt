package com.pulldock.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pulldock.core.designsystem.theme.LocalPdTokens

/**
 * Determinate progress row with human-readable context (FR-068). Progress is described
 * for TalkBack by the summary line, never by the bar alone.
 */
@Composable
fun PdProgress(
    fraction: Float?,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Unknown total: indeterminate bar rather than a fake percentage (FR-070).
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
