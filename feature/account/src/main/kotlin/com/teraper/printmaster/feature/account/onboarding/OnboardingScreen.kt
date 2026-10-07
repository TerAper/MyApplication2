package com.teraper.printmaster.feature.account.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.feature.account.R
import com.teraper.printmaster.feature.account.common.CompanyFields

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.mode != null, onBack = viewModel::onBackToModes)
    OnboardingScreen(
        state = state,
        onModeSelected = viewModel::onModeSelected,
        onBackToModes = viewModel::onBackToModes,
        onOwnerNameChange = viewModel::onOwnerNameChange,
        onCompanyChange = viewModel::onCompanyChange,
        onRegister = viewModel::onRegister,
    )
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onModeSelected: (AccountMode) -> Unit,
    onBackToModes: () -> Unit,
    onOwnerNameChange: (String) -> Unit,
    onCompanyChange: (CompanyDraft) -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = state.mode
    Box(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        if (mode == null) ModeChoice(onModeSelected) else RegistrationForm(state, mode, onBackToModes, onOwnerNameChange, onCompanyChange, onRegister)
    }
}

@Composable
private fun ModeChoice(onModeSelected: (AccountMode) -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.feature_account_welcome),
            modifier = Modifier.padding(top = 32.dp),
            style = MaterialTheme.typography.headlineMedium,
            color = PmTheme.colors.ink,
        )
        Text(
            stringResource(R.string.feature_account_welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            color = PmTheme.colors.inkMuted,
        )
        ModeCard(
            icon = PmIcons.Master,
            title = stringResource(R.string.feature_account_mode_master),
            message = stringResource(R.string.feature_account_mode_master_message),
            onClick = { onModeSelected(AccountMode.MASTER) },
        )
        ModeCard(
            icon = PmIcons.Company,
            title = stringResource(R.string.feature_account_mode_company),
            message = stringResource(R.string.feature_account_mode_company_message),
            onClick = { onModeSelected(AccountMode.COMPANY) },
        )
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, message: String, onClick: () -> Unit) {
    PmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(PmTheme.colors.primaryContainer, PmTheme.shapes.button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = PmTheme.colors.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = PmTheme.colors.ink)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkMuted)
            }
            Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
        }
    }
}

@Composable
private fun RegistrationForm(
    state: OnboardingUiState,
    mode: AccountMode,
    onBack: () -> Unit,
    onOwnerNameChange: (String) -> Unit,
    onCompanyChange: (CompanyDraft) -> Unit,
    onRegister: () -> Unit,
) {
    // Shown before the app's Scaffold exists, so it keeps clear of the system bars itself.
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        PmTopBar(
            title = stringResource(if (mode == AccountMode.MASTER) R.string.feature_account_mode_master else R.string.feature_account_mode_company),
            navigationLabel = stringResource(R.string.feature_account_back),
            onNavigate = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (mode == AccountMode.MASTER) {
                PmTextField(
                    value = state.ownerName,
                    onValueChange = onOwnerNameChange,
                    label = stringResource(R.string.feature_account_your_name),
                    error = if (state.ownerNameMissing) stringResource(R.string.feature_account_error_your_name) else null,
                    capitalization = KeyboardCapitalization.Words,
                )
                SectionTitle(stringResource(R.string.feature_account_first_company))
                Text(
                    stringResource(R.string.feature_account_first_company_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            } else {
                SectionTitle(stringResource(R.string.feature_account_your_company))
            }
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

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ModeChoicePreview() {
    PmTheme { OnboardingScreen(OnboardingUiState(), {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun MasterFormPreview() {
    PmTheme {
        OnboardingScreen(
            OnboardingUiState(mode = AccountMode.MASTER, ownerName = "Արամ", company = CompanyDraft(name = "«Ալֆա» ՍՊԸ", colorIndex = 1)),
            {}, {}, {}, {}, {},
        )
    }
}
