package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme

@Composable
fun PmPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = PmTheme.colors.primary,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = PmTheme.spacing.touchTarget),
        enabled = enabled,
        shape = PmTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = PmTheme.colors.onPrimary),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun PmSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = PmTheme.spacing.touchTarget),
        enabled = enabled,
        shape = PmTheme.shapes.button,
        border = BorderStroke(1.dp, PmTheme.colors.outlineStrong),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = PmTheme.colors.surface, contentColor = PmTheme.colors.primary),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
