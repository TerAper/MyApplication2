package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme

/** Top bar for screens opened from a tab: back (or close) arrow, title, actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmTopBar(
    title: String,
    navigationLabel: String,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector = PmIcons.Back,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onNavigate) { Icon(navigationIcon, contentDescription = navigationLabel) }
        },
        actions = actions,
        // The app's Scaffold already keeps content below the status bar.
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PmTheme.colors.surface,
            titleContentColor = PmTheme.colors.ink,
            navigationIconContentColor = PmTheme.colors.ink,
            actionIconContentColor = PmTheme.colors.ink,
        ),
    )
}
