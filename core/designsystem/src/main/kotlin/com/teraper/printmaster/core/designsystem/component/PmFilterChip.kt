package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme

/** Rounded filter pill; the selected one is dark. */
@Composable
fun PmFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PmTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        shape = PmTheme.shapes.pill,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surface,
            labelColor = c.inkSecondary,
            selectedContainerColor = c.ink,
            selectedLabelColor = c.surface,
        ),
        border = BorderStroke(1.dp, if (selected) c.ink else c.outlineStrong),
    )
}
