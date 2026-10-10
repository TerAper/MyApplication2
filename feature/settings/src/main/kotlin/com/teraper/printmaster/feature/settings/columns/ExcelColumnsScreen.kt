package com.teraper.printmaster.feature.settings.columns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.component.use
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportColumns
import com.teraper.printmaster.core.model.ImportKind
import com.teraper.printmaster.feature.settings.R

@Composable
internal fun ExcelColumnsRoute(onBack: () -> Unit, viewModel: ExcelColumnsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExcelColumnsScreen(
        state = state,
        onBack = onBack,
        onEdit = viewModel::onEdit,
        onEditTextChange = viewModel::onEditTextChange,
        onSaveEdit = viewModel::onSaveEdit,
        onUseDefault = viewModel::onUseDefault,
        onDismissEdit = viewModel::onDismissEdit,
        onResetClick = viewModel::onResetClick,
        onConfirmReset = viewModel::onConfirmReset,
        onDismissReset = viewModel::onDismissReset,
    )
}

@Composable
internal fun ExcelColumnsScreen(
    state: ExcelColumnsUiState,
    onBack: () -> Unit,
    onEdit: (ImportColumn) -> Unit,
    onEditTextChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onUseDefault: () -> Unit,
    onDismissEdit: () -> Unit,
    onResetClick: (ImportKind) -> Unit,
    onConfirmReset: () -> Unit,
    onDismissReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_settings_excel_columns),
            navigationLabel = stringResource(R.string.feature_settings_back),
            onNavigate = onBack,
        )
        if (state.isLoading) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.feature_settings_columns_intro), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
            ImportKind.entries.forEach { kind ->
                val columns = ImportColumn.of(kind)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (kind == ImportKind.INVOICES) R.string.feature_settings_columns_invoices else R.string.feature_settings_columns_bank),
                        Modifier.weight(1f).padding(start = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = PmTheme.colors.inkMuted,
                    )
                    if (columns.any { state.columns.isCustom(it) }) {
                        TextButton(onClick = { onResetClick(kind) }) { Text(stringResource(R.string.feature_settings_columns_reset)) }
                    }
                }
                PmCard(Modifier.fillMaxWidth()) {
                    columns.forEachIndexed { index, column ->
                        if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        ColumnRow(column, state.columns, onClick = { onEdit(column) })
                    }
                }
            }
        }
    }
    state.editing?.let { edit ->
        EditDialog(edit, onEditTextChange, onSaveEdit, onUseDefault, onDismissEdit)
    }
    state.confirmReset?.let {
        PmConfirmDialog(
            title = stringResource(R.string.feature_settings_columns_reset_title),
            message = stringResource(R.string.feature_settings_columns_reset_message),
            confirmText = stringResource(R.string.feature_settings_columns_reset),
            dismissText = stringResource(R.string.feature_settings_cancel),
            onConfirm = onConfirmReset,
            onDismiss = onDismissReset,
        )
    }
}

@Composable
private fun ColumnRow(column: ImportColumn, columns: ImportColumns, onClick: () -> Unit) {
    val custom = columns.isCustom(column)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(column.label(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall)
                if (column.required) PmTag(stringResource(R.string.feature_settings_columns_needed), TagTone.Info)
                if (custom) PmTag(stringResource(R.string.feature_settings_columns_changed), TagTone.Warning)
            }
            Text(
                columns.titles(column).joinToString("  ·  ") { "«$it»" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (custom) PmTheme.colors.primary else PmTheme.colors.inkSecondary,
            )
            Text(column.use(), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
        Icon(PmIcons.Edit, contentDescription = stringResource(R.string.feature_settings_columns_edit), tint = PmTheme.colors.outlineStrong)
    }
}

@Composable
private fun EditDialog(
    edit: ColumnEdit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDefault = edit.text.lines().map { ImportColumns.normalize(it) }.filter { it.isNotEmpty() }.let { it.isEmpty() || it == edit.column.defaults }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(edit.column.label()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.feature_settings_columns_edit_hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                PmTextField(
                    value = edit.text,
                    onValueChange = onTextChange,
                    label = stringResource(R.string.feature_settings_columns_titles),
                    capitalization = KeyboardCapitalization.None,
                    singleLine = false,
                    minLines = 3,
                )
                Text(
                    stringResource(R.string.feature_settings_columns_default, edit.column.defaults.joinToString("  ·  ") { "«$it»" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
                if (!isDefault) {
                    TextButton(onClick = onUseDefault, Modifier.padding(start = 0.dp)) { Text(stringResource(R.string.feature_settings_columns_use_default)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.feature_settings_columns_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) } },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ExcelColumnsScreenPreview() {
    PmTheme {
        ExcelColumnsScreen(
            ExcelColumnsUiState(isLoading = false, columns = ImportColumns().with(ImportColumn.BANK_PAYER, listOf("Վճարող", "Payer"))),
            onBack = {}, onEdit = {}, onEditTextChange = {}, onSaveEdit = {}, onUseDefault = {}, onDismissEdit = {},
            onResetClick = {}, onConfirmReset = {}, onDismissReset = {},
        )
    }
}
