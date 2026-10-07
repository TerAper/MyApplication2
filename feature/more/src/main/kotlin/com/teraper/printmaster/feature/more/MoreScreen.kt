package com.teraper.printmaster.feature.more

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmScreenTitle
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.Company

/** One menu row; [onClick] null = not built yet (shown as "Soon"). [subtitleText] overrides [subtitle]. */
private data class MoreItem(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val subtitle: Int,
    val onClick: (() -> Unit)?,
    val subtitleText: String? = null,
)

private data class MoreSection(@param:StringRes val title: Int, val items: List<MoreItem>)

/** Where the More menu can go; the app wires each to its feature. */
data class MoreActions(
    val onOpenCatalog: () -> Unit = {},
    val onOpenCompanies: () -> Unit = {},
    val onOpenCompany: (Long) -> Unit = {},
    val onOpenMasters: () -> Unit = {},
)

@Composable
internal fun MoreRoute(actions: MoreActions, modifier: Modifier = Modifier, viewModel: MoreViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MoreScreen(state = state, actions = actions, modifier = modifier)
}

@Composable
internal fun MoreScreen(state: MoreUiState, actions: MoreActions, modifier: Modifier = Modifier) {
    val onOpenCatalog = actions.onOpenCatalog
    val account = when (state.mode) {
        AccountMode.MASTER -> listOf(
            MoreItem(
                PmIcons.Company, R.string.feature_more_companies, R.string.feature_more_companies_sub, actions.onOpenCompanies,
                subtitleText = pluralStringResource(R.plurals.feature_more_company_count, state.companies.size, state.companies.size) +
                    (state.defaultCompany?.let { " · " + stringResource(R.string.feature_more_default_company, it.name) } ?: ""),
            ),
        )
        AccountMode.COMPANY -> listOf(
            MoreItem(
                PmIcons.Company, R.string.feature_more_company, R.string.feature_more_company_sub,
                { state.defaultCompany?.let { actions.onOpenCompany(it.id) } },
                subtitleText = state.defaultCompany?.name,
            ),
            MoreItem(PmIcons.Master, R.string.feature_more_masters, R.string.feature_more_masters_sub, actions.onOpenMasters),
        )
        null -> emptyList()
    }
    val sections = listOfNotNull(
        account.takeIf { it.isNotEmpty() }?.let { MoreSection(R.string.feature_more_section_account, it) },
        MoreSection(
            R.string.feature_more_section_work,
            listOf(
                MoreItem(PmIcons.PriceList, R.string.feature_more_price_list, R.string.feature_more_price_list_sub, null),
                MoreItem(PmIcons.Printer, R.string.feature_more_catalog, R.string.feature_more_catalog_sub, onOpenCatalog),
            ),
        ),
        MoreSection(
            R.string.feature_more_section_money,
            listOf(
                MoreItem(PmIcons.Reports, R.string.feature_more_reports, R.string.feature_more_reports_sub, null),
                MoreItem(PmIcons.Export, R.string.feature_more_export, R.string.feature_more_export_sub, null),
                MoreItem(PmIcons.History, R.string.feature_more_imports, R.string.feature_more_imports_sub, null),
            ),
        ),
        MoreSection(
            R.string.feature_more_section_data,
            listOf(
                MoreItem(PmIcons.Backup, R.string.feature_more_backup, R.string.feature_more_backup_sub, null),
                MoreItem(PmIcons.Settings, R.string.feature_more_settings, R.string.feature_more_settings_sub, null),
            ),
        ),
    )
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).verticalScroll(rememberScrollState())) {
        PmScreenTitle(title = stringResource(R.string.feature_more_title))
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEach { section ->
                Text(
                    stringResource(section.title),
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = PmTheme.colors.inkMuted,
                )
                PmCard(Modifier.fillMaxWidth()) {
                    section.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        MoreRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem) {
    val enabled = item.onClick != null
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { item.onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(PmTheme.colors.primaryContainer, PmTheme.shapes.button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = if (enabled) PmTheme.colors.primary else PmTheme.colors.inkMuted)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(item.title),
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) PmTheme.colors.ink else PmTheme.colors.inkMuted,
            )
            Text(item.subtitleText ?: stringResource(item.subtitle), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
        if (enabled) {
            Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
        } else {
            PmTag(stringResource(R.string.feature_more_soon), TagTone.Neutral)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun MoreScreenPreview() {
    PmTheme {
        MoreScreen(
            MoreUiState(AccountMode.MASTER, listOf(Company(1, "«Ալֆա» ՍՊԸ"), Company(2, "Beta")), Company(1, "«Ալֆա» ՍՊԸ")),
            MoreActions(),
        )
    }
}
