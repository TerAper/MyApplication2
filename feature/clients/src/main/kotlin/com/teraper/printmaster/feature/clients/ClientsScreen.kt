package com.teraper.printmaster.feature.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme

/** Entry point used by navigation. Will get its ViewModel here once the tab has data. */
@Composable
internal fun ClientsRoute(modifier: Modifier = Modifier) {
    ClientsScreen(modifier = modifier)
}

/** Draws only what it's given, so a redesign never touches logic. */
@Composable
internal fun ClientsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmScreenTitle(title = stringResource(R.string.feature_clients_title))
        PmEmptyState(
            icon = PmIcons.Clients,
            title = stringResource(R.string.feature_clients_empty_title),
            message = stringResource(R.string.feature_clients_empty_message),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ClientsScreenPreview() {
    PmTheme { ClientsScreen() }
}
