package com.teraper.printmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.R
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.CompanyColors
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Company

/** Thin strip at the top: which company's data is shown; tap to switch. */
@Composable
internal fun CompanyStrip(company: Company, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PmTheme.colors.surface)
            .statusBarsPadding()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompanyBadge(company.initials, company.colorIndex, size = 28.dp)
        Text(
            company.name,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleSmall,
            color = CompanyColors.of(company.colorIndex),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(PmIcons.Dropdown, contentDescription = stringResource(R.string.switch_company), tint = PmTheme.colors.inkMuted)
    }
    HorizontalDivider(color = PmTheme.colors.surfaceMuted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanySwitchSheet(
    companies: List<Company>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PmTheme.colors.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                stringResource(R.string.switch_company_title),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            companies.forEach { company ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(company.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CompanyBadge(company.initials, company.colorIndex, size = 36.dp)
                    Text(company.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (company.id == activeId) Icon(PmIcons.Check, contentDescription = null, tint = PmTheme.colors.primary)
                }
            }
            TextButton(onClick = onManage, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.manage_companies))
            }
        }
    }
}
