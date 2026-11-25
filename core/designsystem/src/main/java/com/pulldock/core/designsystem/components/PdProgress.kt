package com.pulldock.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pulldock.core.designsystem.theme.LocalPdTokens

/** Animated progress row with a text alternative for TalkBack. */
@Composable
fun PdProgress(
    fraction: Float?,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    val target = fraction?.coerceIn(0f, 1f) ?: 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = tokens.motion.base),
        label = "pd_progress",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}