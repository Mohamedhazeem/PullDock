package com.pulldock.core.designsystem.components

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Primary PullDock button with explicit loading/disabled states (FR-068). While
 * [loading], the button is disabled and shows progress instead of inviting a second
 * submit — no misleading dead-time between tap and response.
 */
@Composable
fun PdButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
    ) {
        Text(label)
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Secondary/text button with the same loading discipline. */
@Composable
fun PdTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading) {
        Text(label, color = contentColor)
    }
}
