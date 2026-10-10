package com.teraper.printmaster.feature.account.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.feature.account.R
import com.teraper.printmaster.feature.account.common.CompanyFields

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onOwnerNameChange = viewModel::onOwnerNameChange,
        onCompanyChange = viewModel::onCompanyChange,
        onRegister = viewModel::onRegister,
    )
}

/** One registration for everyone: your name and your first company. */
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onOwnerNameChange: (String) -> Unit,
    onCompanyChange: (CompanyDraft) -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Shown before the app's Scaffold exists, so it keeps clear of the system bars itself.
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).systemBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.feature_account_welcome),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = PmTheme.colors.ink,
            )
            Text(stringResource(R.string.feature_account_welcome_message), style = MaterialTheme.typography.bodyLarge, color = PmTheme.colors.inkMuted)
            state.email?.let { email ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(PmIcons.Check, contentDescription = null, tint = PmTheme.colors.paid, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.feature_account_signed_in_as, email), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkSecondary)
                }
            }
            PmTextField(
                value = state.ownerName,
                onValueChange = onOwnerNameChange,
                label = stringResource(R.string.feature_account_your_name),
                error = if (state.ownerNameMissing) stringResource(R.string.feature_account_error_your_name) else null,
                capitalization = KeyboardCapitalization.Words,
            )
            SectionTitle(stringResource(R.string.feature_account_first_company))
            Text(stringResource(R.string.feature_account_first_company_message), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            CompanyFields(draft = state.company, errors = state.errors, taxIdTakenBy = null, onChange = onCompanyChange)
        }
        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(
                text = stringResource(R.string.feature_account_start),
                onClick = onRegister,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 6.dp, start = 2.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun OnboardingPreview() {
    PmTheme {
        OnboardingScreen(
            OnboardingUiState(ownerName = "Արամ", company = CompanyDraft(name = "«Ալֆա» ՍՊԸ", colorIndex = 1), email = "aram@gmail.com"),
            {}, {}, {},
        )
    }
}
