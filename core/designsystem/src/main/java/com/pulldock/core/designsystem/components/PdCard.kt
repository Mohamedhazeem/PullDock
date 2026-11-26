package com.pulldock.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens

/**
 * Warm, low-elevation surface used as the base for cards and sections.
 *
 * When [flat] is true the card loses its border, elevation, and container color so it can
 * sit inside another card (e.g. grouped children) without a nested-card look.
 */
@Composable
fun PdCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    flat: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = LocalPdTokens.current
    val colors = CardDefaults.cardColors(
        containerColor = if (flat) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = if (flat) 0.dp else tokens.elevation.low)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(tokens.radius.lg)
    val border = if (flat) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Box(Modifier.padding(tokens.spacing.md)) { content() }
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Box(Modifier.padding(tokens.spacing.md)) { content() }
        }
    }
}