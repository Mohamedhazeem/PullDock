package com.pulldock.core.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Destructive-action confirmation dialog (FR-041, T039). Reversible actions use undo
 * (snackbar) instead; genuinely destructive operations (remove task, delete file)
 * require this explicit confirmation.
 *
 * @param confirmLabel label of the destructive confirm action (e.g. "Remove").
 * @param onConfirm invoked only on explicit confirm; dismissal is always safe.
 */
@Composable
fun PdConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}
