package com.pulldock.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens

/** Semantic tone for a compact status badge. */
enum class PdStatusTone { Success, Warning, Info, Neutral, Error }

/** Compact icon + label status chip. Never rely on color alone. */
@Composable
fun PdStatusBadge(
    label: String,
    icon: ImageVector,
    tone: PdStatusTone,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    val contentColor = when (tone) {
        PdStatusTone.Success -> tokens.status.success
        PdStatusTone.Warning -> tokens.status.warning
        PdStatusTone.Info -> tokens.status.info
        PdStatusTone.Neutral -> tokens.status.neutral
        PdStatusTone.Error -> MaterialTheme.colorScheme.error
    }
    val containerColor = contentColor.copy(alpha = 0.13f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.radius.sm))
            .background(containerColor)
            .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(start = tokens.spacing.xs),
        )
    }
}