package com.pulldock.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens

/** Small geometric PullDock mark used in onboarding and brand moments. */
@Composable
fun PdBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val stroke = LocalPdTokens.current.spacing.xs
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(color = tint, radius = radius * 0.92f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke.toPx()))
        drawLine(
            color = tint,
            start = Offset(center.x - radius * 0.42f, center.y + radius * 0.18f),
            end = Offset(center.x, center.y - radius * 0.46f),
            strokeWidth = stroke.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(center.x, center.y - radius * 0.46f),
            end = Offset(center.x + radius * 0.42f, center.y + radius * 0.18f),
            strokeWidth = stroke.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(center.x - radius * 0.58f, center.y + radius * 0.42f),
            end = Offset(center.x + radius * 0.58f, center.y + radius * 0.42f),
            strokeWidth = stroke.toPx(),
            cap = StrokeCap.Round,
        )
    }
}