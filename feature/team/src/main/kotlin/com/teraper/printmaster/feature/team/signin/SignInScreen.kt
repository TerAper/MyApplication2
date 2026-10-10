package com.teraper.printmaster.feature.team.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.feature.team.R
import com.teraper.printmaster.feature.team.rememberGoogleSignIn

/** [onSkip]: only for test builds on an emulator, which has no Google account. */
@Composable
fun SignInRoute(onSkip: (() -> Unit)?, viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val signIn = rememberGoogleSignIn(viewModel.clientId, viewModel::onToken)
    SignInScreen(
        state = state,
        onSignIn = {
            viewModel.onSignInStarted()
            signIn()
        },
        onSkip = onSkip,
    )
}

@Composable
internal fun SignInScreen(state: SignInUiState, onSignIn: () -> Unit, onSkip: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).systemBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.padding(top = 32.dp).size(64.dp).background(PmTheme.colors.primaryContainer, PmTheme.shapes.button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PmIcons.Printer, contentDescription = null, tint = PmTheme.colors.primary, modifier = Modifier.size(36.dp))
            }
            Text(stringResource(R.string.feature_team_start_title), style = MaterialTheme.typography.headlineMedium, color = PmTheme.colors.ink)
            Text(stringResource(R.string.feature_team_start_message), style = MaterialTheme.typography.bodyLarge, color = PmTheme.colors.inkMuted)
            Reason(PmIcons.Backup, stringResource(R.string.feature_team_start_backup))
            Reason(PmIcons.Share, stringResource(R.string.feature_team_start_share))
            Reason(PmIcons.Lock, stringResource(R.string.feature_team_start_private))
        }
        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.failed) {
                Text(stringResource(R.string.feature_team_error_sign_in), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.debt)
            }
            PmPrimaryButton(
                text = stringResource(R.string.feature_team_sign_in),
                onClick = onSignIn,
                enabled = !state.signingIn,
                modifier = Modifier.fillMaxWidth(),
                icon = PmIcons.Google,
            )
            if (onSkip != null) {
                TextButton(onClick = onSkip, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.feature_team_start_skip_test))
                }
            }
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PmTheme.colors.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun SignInScreenPreview() {
    PmTheme { SignInScreen(SignInUiState(failed = true), onSignIn = {}, onSkip = {}) }
}
