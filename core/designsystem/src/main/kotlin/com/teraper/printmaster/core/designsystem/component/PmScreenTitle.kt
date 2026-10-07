package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme

/** Large title at the top of each main tab, with an optional small line above it. */
@Composable
fun PmScreenTitle(title: String, modifier: Modifier = Modifier, overline: String? = null) {
    Column(modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
        if (overline != null) {
            Text(overline, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = PmTheme.colors.ink)
    }
}
