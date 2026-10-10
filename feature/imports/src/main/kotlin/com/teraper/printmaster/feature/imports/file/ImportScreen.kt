package com.teraper.printmaster.feature.imports.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyCheck
import com.teraper.printmaster.core.model.ImportKind
import com.teraper.printmaster.core.model.ImportPreview
import com.teraper.printmaster.core.model.MissingColumns
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.feature.imports.R
import java.time.LocalDate

/** Excel files are sometimes shared as plain binary, so both types are offered. */
private val EXCEL_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

@Composable
internal fun ImportRoute(
    onBack: () -> Unit,
    onReviewPayments: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenColumns: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onFilePicked(uri?.toString(), uri?.let { context.displayName(it) }.orEmpty())
    }
    ImportScreen(
        state = state,
        actions = ImportActions(
            onBack = onBack,
            onPickFile = { picker.launch(EXCEL_TYPES) },
            onSaveIdentityChange = viewModel::onSaveIdentityChange,
            onImport = viewModel::onImportClick,
            onConfirmDifferentCompany = viewModel::onConfirmDifferentCompany,
            onDismissDifferentCompany = viewModel::onDismissDifferentCompany,
            onStartOver = viewModel::onStartOver,
            onReviewPayments = onReviewPayments,
            onOpenHistory = onOpenHistory,
            onOpenColumns = onOpenColumns,
        ),
    )
}

internal data class ImportActions(
    val onBack: () -> Unit = {},
    val onPickFile: () -> Unit = {},
    val onSaveIdentityChange: (Boolean) -> Unit = {},
    val onImport: () -> Unit = {},
    val onConfirmDifferentCompany: () -> Unit = {},
    val onDismissDifferentCompany: () -> Unit = {},
    val onStartOver: () -> Unit = {},
    val onReviewPayments: () -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenColumns: () -> Unit = {},
)

@Composable
internal fun ImportScreen(state: ImportUiState, actions: ImportActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_imports_title),
            navigationLabel = stringResource(R.string.feature_imports_back),
            onNavigate = actions.onBack,
            actions = {
                TextButton(onClick = actions.onOpenHistory) {
                    Text(stringResource(R.string.feature_imports_history))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                ImportUiState.PickFile -> PickFile(actions)
                ImportUiState.Reading -> Busy(stringResource(R.string.feature_imports_reading))
                is ImportUiState.Previewing -> PreviewContent(state, actions)
                is ImportUiState.Done -> DoneContent(state, actions)
                is ImportUiState.Failed -> if (state.missing != null) {
                    MissingColumnsContent(state.missing, actions)
                } else {
                    PmEmptyState(
                        icon = PmIcons.Warning,
                        title = stringResource(R.string.feature_imports_failed_title),
                        message = stringResource(
                            when (state.error) {
                                ImportError.NOT_EXCEL -> R.string.feature_imports_error_not_excel
                                ImportError.UNKNOWN_LAYOUT -> R.string.feature_imports_error_layout
                                ImportError.NO_COMPANY -> R.string.feature_imports_error_company
                                ImportError.NOTHING_TO_IMPORT -> R.string.feature_imports_error_nothing
                            },
                        ),
                    )
                    PmPrimaryButton(stringResource(R.string.feature_imports_pick_other), actions.onPickFile, Modifier.fillMaxWidth(), icon = PmIcons.ImportExcel)
                }
            }
        }
    }
    if (state is ImportUiState.Previewing && state.askDifferentCompany) {
        PmConfirmDialog(
            title = stringResource(R.string.feature_imports_wrong_company_title),
            message = stringResource(R.string.feature_imports_wrong_company_message, state.preview.company.name),
            confirmText = stringResource(R.string.feature_imports_import_anyway),
            dismissText = stringResource(R.string.feature_imports_cancel),
            onConfirm = actions.onConfirmDifferentCompany,
            onDismiss = actions.onDismissDifferentCompany,
            destructive = true,
        )
    }
}

/** The file looks like an invoice export or a bank statement, but needed columns weren't found. */
@Composable
private fun MissingColumnsContent(missing: MissingColumns, actions: ImportActions) {
    PmEmptyState(
        icon = PmIcons.Warning,
        title = stringResource(R.string.feature_imports_failed_title),
        message = stringResource(
            if (missing.kind == ImportKind.INVOICES) R.string.feature_imports_missing_invoices else R.string.feature_imports_missing_bank,
        ),
    )
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.feature_imports_missing_list), style = MaterialTheme.typography.titleSmall)
            missing.missing.forEach { column ->
                Text("• " + column.label(), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.feature_imports_missing_hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
    }
    PmPrimaryButton(stringResource(R.string.feature_imports_open_columns), actions.onOpenColumns, Modifier.fillMaxWidth(), icon = PmIcons.Settings)
    PmSecondaryButton(stringResource(R.string.feature_imports_pick_other), actions.onPickFile, Modifier.fillMaxWidth(), icon = PmIcons.ImportExcel)
}

@Composable
private fun PickFile(actions: ImportActions) {
    Text(stringResource(R.string.feature_imports_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
    KindHint(PmIcons.Invoice, R.string.feature_imports_kind_invoices, R.string.feature_imports_kind_invoices_hint)
    KindHint(PmIcons.Payments, R.string.feature_imports_kind_bank, R.string.feature_imports_kind_bank_hint)
    PmPrimaryButton(stringResource(R.string.feature_imports_pick), actions.onPickFile, Modifier.fillMaxWidth(), icon = PmIcons.ImportExcel)
    Text(stringResource(R.string.feature_imports_safe_note), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
}

@Composable
private fun KindHint(icon: ImageVector, title: Int, hint: Int) {
    PmCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = PmTheme.colors.primary)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            }
        }
    }
}

@Composable
private fun Busy(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), color = PmTheme.colors.primary, strokeWidth = 3.dp)
        Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PreviewContent(state: ImportUiState.Previewing, actions: ImportActions) {
    val p = state.preview
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (p.kind == ImportKind.INVOICES) R.string.feature_imports_kind_invoices else R.string.feature_imports_kind_bank),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(p.fileName, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            val from = p.from
            val to = p.to
            if (from != null && to != null) {
                Text(
                    stringResource(R.string.feature_imports_period, from.formatShort(), to.formatShort()),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
        }
    }
    CompanyGuard(state, actions)
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Line(stringResource(R.string.feature_imports_rows), p.rows.toString())
            Line(stringResource(R.string.feature_imports_new), p.newRows.toString(), strong = true)
            if (p.alreadyImported > 0) Line(stringResource(R.string.feature_imports_already), p.alreadyImported.toString())
            if (p.changed > 0) Line(stringResource(R.string.feature_imports_changed), p.changed.toString())
            if (p.kind == ImportKind.INVOICES && p.newClients > 0) Line(stringResource(R.string.feature_imports_new_clients), p.newClients.toString())
            if (p.kind == ImportKind.BANK_STATEMENT && p.newRows > 0) {
                Line(stringResource(R.string.feature_imports_auto), p.autoMatched.toString())
                Line(stringResource(R.string.feature_imports_to_confirm), p.toConfirm.toString())
            }
            if (p.skipped > 0) Line(stringResource(R.string.feature_imports_skipped), p.skipped.toString())
            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (p.kind == ImportKind.INVOICES) R.string.feature_imports_new_debt else R.string.feature_imports_new_paid),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                AmountText(p.newAmount, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (state.importing) {
        Busy(stringResource(R.string.feature_imports_importing))
    } else if (state.hasSomethingNew) {
        PmPrimaryButton(
            text = stringResource(R.string.feature_imports_import_into, p.company.name),
            onClick = actions.onImport,
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (p.companyCheck == CompanyCheck.DIFFERENT) PmTheme.colors.error else PmTheme.colors.primary,
        )
    } else {
        Text(stringResource(R.string.feature_imports_nothing_new), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
    }
    PmSecondaryButton(stringResource(R.string.feature_imports_pick_other), actions.onStartOver, Modifier.fillMaxWidth())
}

/** Whose file is it? A red card when it names another ՀՎՀՀ or account. */
@Composable
private fun CompanyGuard(state: ImportUiState.Previewing, actions: ImportActions) {
    val p = state.preview
    val fileId = listOfNotNull(
        p.fileTaxId?.let { stringResource(R.string.feature_imports_file_tax_id, it) },
        p.fileAccount?.let { stringResource(R.string.feature_imports_file_account, it) },
    ).joinToString(" · ")
    val (container, content) = when (p.companyCheck) {
        CompanyCheck.MATCHES -> PmTheme.colors.paidContainer to PmTheme.colors.paid
        CompanyCheck.DIFFERENT -> PmTheme.colors.debtContainer to PmTheme.colors.error
        else -> PmTheme.colors.warningContainer to PmTheme.colors.warning
    }
    Column(
        Modifier.fillMaxWidth().background(container, PmTheme.shapes.button).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompanyBadge(p.company.initials, p.company.colorIndex)
            Text(
                stringResource(
                    when (p.companyCheck) {
                        CompanyCheck.MATCHES -> R.string.feature_imports_check_ok
                        CompanyCheck.DIFFERENT -> R.string.feature_imports_check_different
                        CompanyCheck.COMPANY_HAS_NONE -> R.string.feature_imports_check_company_none
                        CompanyCheck.FILE_HAS_NONE -> R.string.feature_imports_check_file_none
                    },
                    p.company.name,
                ),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = content,
            )
        }
        if (fileId.isNotEmpty()) Text(fileId, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkSecondary)
        p.fileOwner?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkSecondary) }
        p.belongsTo?.let {
            Text(stringResource(R.string.feature_imports_belongs_to, it.name), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.error)
        }
        if (p.companyCheck == CompanyCheck.COMPANY_HAS_NONE) {
            Row(
                Modifier.fillMaxWidth().clickable { actions.onSaveIdentityChange(!state.saveIdentity) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.saveIdentity,
                    onCheckedChange = actions.onSaveIdentityChange,
                    colors = CheckboxDefaults.colors(checkedColor = PmTheme.colors.primary),
                )
                Text(stringResource(R.string.feature_imports_save_identity, p.company.name), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false, color: Color = PmTheme.colors.ink) {
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
        Text(value, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun DoneContent(state: ImportUiState.Done, actions: ImportActions) {
    val r = state.result
    PmEmptyState(
        icon = PmIcons.Check,
        title = stringResource(R.string.feature_imports_done_title),
        message = buildList {
            add(pluralStringResource(R.plurals.feature_imports_done_added, r.added, r.added))
            if (r.updated > 0) add(pluralStringResource(R.plurals.feature_imports_done_updated, r.updated, r.updated))
            if (r.createdClients > 0) add(pluralStringResource(R.plurals.feature_imports_done_clients, r.createdClients, r.createdClients))
        }.joinToString("\n"),
    )
    if (r.pending > 0) {
        PmPrimaryButton(
            text = pluralStringResource(R.plurals.feature_imports_review, r.pending, r.pending),
            onClick = actions.onReviewPayments,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PmSecondaryButton(stringResource(R.string.feature_imports_another), actions.onStartOver, Modifier.fillMaxWidth(), icon = PmIcons.ImportExcel)
}

private fun Context.displayName(uri: Uri): String = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment.orEmpty()

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ImportPreviewScreen() {
    PmTheme {
        ImportScreen(
            ImportUiState.Previewing(
                ImportPreview(
                    ImportKind.BANK_STATEMENT, "bank.xlsx", Company(1, "Alfa Print", taxId = "11111111"), CompanyCheck.DIFFERENT,
                    belongsTo = Company(2, "ՍՈԽԱԿ ԱՁ"), fileTaxId = "1386596363", fileAccount = "1150018363093024",
                    from = LocalDate.of(2025, 1, 16), to = LocalDate.of(2025, 8, 1),
                    rows = 237, newRows = 237, newAmount = Money.ofDram(4_400_000), autoMatched = 230, toConfirm = 7, skipped = 87,
                ),
            ),
            ImportActions(),
        )
    }
}
