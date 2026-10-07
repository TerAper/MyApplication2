package com.teraper.printmaster.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.teraper.printmaster.core.designsystem.theme.PmTheme

/** Yes/no question. [destructive] paints the confirm button red (delete, discard). */
@Composable
fun PmConfirmDialog(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) PmTheme.colors.error else PmTheme.colors.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
        containerColor = PmTheme.colors.surface,
    )
}

/** Simple message with one OK button. */
@Composable
fun PmMessageDialog(title: String, message: String, okText: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(okText) } },
        containerColor = PmTheme.colors.surface,
    )
}
