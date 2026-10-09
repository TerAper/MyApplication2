package com.teraper.printmaster.feature.imports.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ImportBatch
import com.teraper.printmaster.core.model.ImportKind
import com.teraper.printmaster.feature.imports.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

@Composable
internal fun HistoryRoute(onBack: () -> Unit, onImport: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, onBack, onImport, viewModel::onUndoClick, viewModel::onConfirmUndo, viewModel::onDismissUndo)
}

@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onUndo: (ImportBatch) -> Unit = {},
    onConfirmUndo: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_imports_history_title),
            navigationLabel = stringResource(R.string.feature_imports_back),
            onNavigate = onBack,
        )
        if (state.isLoading) return@Column
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PmPrimaryButton(stringResource(R.string.feature_imports_title), onImport, Modifier.fillMaxWidth(), icon = PmIcons.ImportExcel) }
            if (state.batches.isEmpty()) {
                item {
                    PmEmptyState(
                        icon = PmIcons.History,
                        title = stringResource(R.string.feature_imports_history_empty),
                        message = stringResource(R.string.feature_imports_history_empty_message),
                    )
                }
            }
            items(state.batches, key = { it.id }) { batch ->
                PmCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (batch.kind == ImportKind.INVOICES) PmIcons.Invoice else PmIcons.Payments, contentDescription = null, tint = PmTheme.colors.primary)
                        Column(Modifier.weight(1f)) {
                            Text(batch.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                batch.importedAt.format(WHEN) + " · " + stringResource(R.string.feature_imports_history_counts, batch.addedCount, batch.skippedCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = PmTheme.colors.inkMuted,
                            )
                        }
                        TextButton(onClick = { onUndo(batch) }) { Text(stringResource(R.string.feature_imports_undo), color = PmTheme.colors.error) }
                    }
                }
            }
        }
    }
    state.confirmUndo?.let { batch ->
        PmConfirmDialog(
            title = stringResource(R.string.feature_imports_undo_title),
            message = stringResource(R.string.feature_imports_undo_message, batch.fileName, batch.addedCount),
            confirmText = stringResource(R.string.feature_imports_undo),
            dismissText = stringResource(R.string.feature_imports_cancel),
            onConfirm = onConfirmUndo,
            onDismiss = onDismissUndo,
            destructive = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun HistoryPreview() {
    PmTheme {
        HistoryScreen(
            HistoryUiState(false, listOf(ImportBatch(1, ImportKind.BANK_STATEMENT, "bank.xlsx", LocalDateTime.of(2026, 10, 9, 18, 0), 237, 230, 7))),
            onBack = {}, onImport = {},
        )
    }
}
