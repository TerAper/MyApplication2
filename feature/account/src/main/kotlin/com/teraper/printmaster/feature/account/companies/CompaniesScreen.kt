package com.teraper.printmaster.feature.account.companies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.feature.account.R

@Composable
internal fun CompaniesRoute(
    onBack: () -> Unit,
    onCompanyClick: (Long) -> Unit,
    onAddCompany: () -> Unit,
    viewModel: CompaniesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompaniesScreen(state, onBack, onCompanyClick, onAddCompany)
}

@Composable
internal fun CompaniesScreen(
    state: CompaniesUiState,
    onBack: () -> Unit,
    onCompanyClick: (Long) -> Unit,
    onAddCompany: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_account_companies),
            navigationLabel = stringResource(R.string.feature_account_back),
            onNavigate = onBack,
        )
        if (state.isLoading) return@Column
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.companies.forEach { company ->
                CompanyRow(
                    company = company,
                    isDefault = company.id == state.defaultCompanyId,
                    isActive = company.id == state.activeCompanyId,
                    onClick = { onCompanyClick(company.id) },
                )
            }
            Text(
                stringResource(R.string.feature_account_companies_help),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
            if (state.canAdd) {
                PmSecondaryButton(
                    text = stringResource(R.string.feature_account_add_company),
                    onClick = onAddCompany,
                    icon = PmIcons.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CompanyRow(company: Company, isDefault: Boolean, isActive: Boolean, onClick: () -> Unit) {
    PmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CompanyBadge(company.initials, company.colorIndex, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(company.name, style = MaterialTheme.typography.titleSmall, color = PmTheme.colors.ink)
                val details = listOfNotNull(
                    company.taxId?.let { stringResource(R.string.feature_account_tax_id_value, it) },
                    company.bankAccounts.firstOrNull(),
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                }
                if (isDefault || isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isDefault) PmTag(stringResource(R.string.feature_account_default), TagTone.Info)
                        if (isActive) PmTag(stringResource(R.string.feature_account_viewing), TagTone.Paid)
                    }
                }
            }
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun CompaniesScreenPreview() {
    PmTheme {
        CompaniesScreen(
            CompaniesUiState(
                isLoading = false,
                companies = listOf(
                    Company(1, "«Ալֆա Սերվիս» ՍՊԸ", "01234567", listOf("2470000012345678"), 0),
                    Company(2, "Beta Print", null, emptyList(), 3),
                ),
                defaultCompanyId = 1,
                activeCompanyId = 2,
                canAdd = true,
            ),
            {}, {}, {},
        )
    }
}
