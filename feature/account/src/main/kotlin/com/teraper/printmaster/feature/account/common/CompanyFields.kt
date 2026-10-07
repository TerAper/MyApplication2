package com.teraper.printmaster.feature.account.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.CompanyColors
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyDraftError
import com.teraper.printmaster.core.model.CompanyNames
import com.teraper.printmaster.feature.account.R

/** Name, ՀՎՀՀ, bank accounts and color: used by registration and the company form. */
@Composable
internal fun CompanyFields(
    draft: CompanyDraft,
    errors: Set<CompanyDraftError>,
    taxIdTakenBy: String?,
    onChange: (CompanyDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PmTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = stringResource(R.string.feature_account_company_name),
            placeholder = stringResource(R.string.feature_account_company_name_hint),
            error = if (CompanyDraftError.NAME_REQUIRED in errors) stringResource(R.string.feature_account_error_name) else null,
            capitalization = KeyboardCapitalization.Words,
        )
        PmTextField(
            value = draft.taxId,
            onValueChange = { value -> onChange(draft.copy(taxId = value.filter { it.isDigit() || it == ' ' }.take(12))) },
            label = stringResource(R.string.feature_account_tax_id),
            placeholder = stringResource(R.string.feature_account_tax_id_hint),
            error = when {
                taxIdTakenBy != null -> stringResource(R.string.feature_account_error_tax_id_taken, taxIdTakenBy)
                CompanyDraftError.TAX_ID_FORMAT in errors -> stringResource(R.string.feature_account_error_tax_id)
                else -> null
            },
            keyboardType = KeyboardType.Number,
        )
        PmTextField(
            value = draft.bankAccounts,
            onValueChange = { onChange(draft.copy(bankAccounts = it)) },
            label = stringResource(R.string.feature_account_bank_accounts),
            placeholder = stringResource(R.string.feature_account_bank_accounts_hint),
            error = if (CompanyDraftError.BANK_ACCOUNT_FORMAT in errors) stringResource(R.string.feature_account_error_bank_account) else null,
            keyboardType = KeyboardType.Number,
            singleLine = false,
        )
        Text(
            stringResource(R.string.feature_account_color),
            style = MaterialTheme.typography.labelSmall,
            color = PmTheme.colors.inkMuted,
            modifier = Modifier.padding(start = 2.dp),
        )
        ColorPicker(initials = CompanyNames.initials(draft.name), selected = draft.colorIndex, onSelect = { onChange(draft.copy(colorIndex = it)) })
    }
}

/** The palette as badges showing the company's own initials, so the user sees the result. */
@Composable
private fun ColorPicker(initials: String, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CompanyColors.palette.indices.forEach { index ->
            val isSelected = index == selected
            Box(
                Modifier
                    .size(40.dp)
                    .border(if (isSelected) 3.dp else 0.dp, if (isSelected) PmTheme.colors.ink else PmTheme.colors.background, CircleShape)
                    .padding(3.dp)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                CompanyBadge(initials = initials, colorIndex = index, size = 34.dp)
            }
        }
    }
}
