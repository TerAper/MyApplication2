package com.teraper.printmaster.feature.team.join

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.feature.team.R
import com.teraper.printmaster.feature.team.rememberGoogleSignIn

/** "I work for a company": shown instead of the registration form. */
@Composable
fun JoinCompanyRoute(onBack: () -> Unit, viewModel: JoinViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val signIn = rememberGoogleSignIn(viewModel.googleClientId, viewModel::onSignInResult)
    JoinScreen(state, onBack, signIn, viewModel::onNameChange, viewModel::onCodeChange, viewModel::onJoin)
}

@Composable
internal fun JoinScreen(
    state: JoinUiState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(R.string.feature_team_join_title),
            navigationLabel = stringResource(R.string.feature_team_back),
            onNavigate = onBack,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.feature_team_join_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
            when {
                !state.available -> Text(stringResource(R.string.feature_team_unavailable_message), color = PmTheme.colors.error)
                state.email == null -> {
                    Text(stringResource(R.string.feature_team_join_step_sign_in), style = MaterialTheme.typography.titleSmall)
                    PmPrimaryButton(stringResource(R.string.feature_team_sign_in), onSignIn, Modifier.fillMaxWidth(), enabled = !state.busy)
                }
                else -> {
                    Text(stringResource(R.string.feature_team_signed_in, state.email), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.paid)
                    PmTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = stringResource(R.string.feature_team_your_name),
                        error = if (state.error == JoinError.NAME_REQUIRED) stringResource(R.string.feature_team_error_name) else null,
                        capitalization = KeyboardCapitalization.Words,
                    )
                    PmTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        label = stringResource(R.string.feature_team_code_label),
                        placeholder = "K7PQ2MXA",
                        error = when (state.error) {
                            JoinError.CODE_REQUIRED -> stringResource(R.string.feature_team_error_code_short)
                            JoinError.WRONG_CODE -> stringResource(R.string.feature_team_error_wrong_code)
                            else -> null
                        },
                        capitalization = KeyboardCapitalization.Characters,
                    )
                    PmPrimaryButton(stringResource(R.string.feature_team_join), onJoin, Modifier.fillMaxWidth(), icon = PmIcons.Check, enabled = !state.busy)
                }
            }
            when (state.error) {
                JoinError.SIGN_IN -> Text(stringResource(R.string.feature_team_error_sign_in), color = PmTheme.colors.error, style = MaterialTheme.typography.bodySmall)
                JoinError.FAILED -> Text(stringResource(R.string.feature_team_error_join), color = PmTheme.colors.error, style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = PmTheme.colors.primary, strokeWidth = 3.dp)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun JoinPreview() {
    PmTheme { JoinScreen(JoinUiState(email = "armen@gmail.com", name = "Armen", code = "K7PQ"), {}, {}, {}, {}, {}) }
}
