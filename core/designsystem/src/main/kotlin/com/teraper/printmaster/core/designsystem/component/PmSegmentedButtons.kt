package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.teraper.printmaster.core.designsystem.theme.PmTheme

/** Pick exactly one of a few options, e.g. Firm / Private. */
@Composable
fun <T> PmSegmentedButtons(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = PmTheme.colors.primary,
                    activeContentColor = PmTheme.colors.onPrimary,
                    inactiveContainerColor = PmTheme.colors.surface,
                    inactiveContentColor = PmTheme.colors.inkSecondary,
                ),
                icon = {},
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}
