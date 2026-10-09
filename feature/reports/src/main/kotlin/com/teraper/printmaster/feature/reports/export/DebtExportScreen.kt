package com.teraper.printmaster.feature.reports.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.data.repository.DebtReportLabels
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmSegmentedButtons
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.feature.reports.R
import java.io.File

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
internal fun DebtExportRoute(onBack: () -> Unit, viewModel: DebtExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val labels = debtReportLabels()
    val currentLabels by rememberUpdatedState(labels)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) {
        viewModel.onSaveTo(it?.toString(), currentLabels)
    }
    val shareTitle = stringResource(R.string.feature_reports_export_share_title)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DebtExportEvent.PickSaveLocation -> saveLauncher.launch(event.fileName)
                is DebtExportEvent.Share -> context.shareFile(File(event.path), shareTitle)
            }
        }
    }
    DebtExportScreen(
        state = state,
        onBack = onBack,
        onScopeChange = viewModel::onScopeChange,
        onSave = viewModel::onSaveClick,
        onShare = { viewModel.onShare(labels) },
        onDismissDialog = viewModel::onDismissDialog,
    )
}

@Composable
internal fun DebtExportScreen(
    state: DebtExportUiState,
    onBack: () -> Unit,
    onScopeChange: (DebtExportScope) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_reports_export_title),
            navigationLabel = stringResource(R.string.feature_reports_back),
            onNavigate = onBack,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PmCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.company?.let { CompanyBadge(it.initials, it.colorIndex, size = 40.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(state.company?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            pluralStringResource(R.plurals.feature_reports_debtors, state.debtorCount, state.debtorCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = PmTheme.colors.inkMuted,
                        )
                    }
                    AmountText(state.totalDebt, tone = AmountTone.Debt, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                stringResource(R.string.feature_reports_export_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkSecondary,
            )
            PmSegmentedButtons(
                options = DebtExportScope.entries,
                selected = state.scope,
                onSelect = onScopeChange,
                label = {
                    when (it) {
                        DebtExportScope.DEBTORS -> stringResource(R.string.feature_reports_export_debtors, state.debtorCount)
                        DebtExportScope.ALL -> stringResource(R.string.feature_reports_export_all, state.clientCount)
                    }
                },
            )
            val enabled = !state.isWriting && state.rowCount > 0
            PmPrimaryButton(
                text = stringResource(R.string.feature_reports_export_share),
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                icon = PmIcons.Share,
                enabled = enabled,
            )
            PmSecondaryButton(
                text = stringResource(R.string.feature_reports_export_save),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                icon = PmIcons.Export,
                enabled = enabled,
            )
            if (state.rowCount == 0) {
                Text(
                    stringResource(R.string.feature_reports_export_nothing),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
            if (state.isWriting) {
                CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = PmTheme.colors.primary, strokeWidth = 3.dp)
            }
        }
    }
    when (state.dialog) {
        null -> Unit
        DebtExportDialog.SAVED -> PmMessageDialog(
            title = stringResource(R.string.feature_reports_export_saved_title),
            message = stringResource(R.string.feature_reports_export_saved_message),
            okText = stringResource(R.string.feature_reports_ok),
            onDismiss = onDismissDialog,
        )
        DebtExportDialog.FAILED -> PmMessageDialog(
            title = stringResource(R.string.feature_reports_export_failed_title),
            message = stringResource(R.string.feature_reports_export_failed_message),
            okText = stringResource(R.string.feature_reports_ok),
            onDismiss = onDismissDialog,
        )
    }
}

@Composable
private fun debtReportLabels() = DebtReportLabels(
    title = stringResource(R.string.feature_reports_xlsx_title),
    client = stringResource(R.string.feature_reports_xlsx_client),
    taxId = stringResource(R.string.feature_reports_xlsx_tax_id),
    phone = stringResource(R.string.feature_reports_xlsx_phone),
    charged = stringResource(R.string.feature_reports_xlsx_charged),
    paid = stringResource(R.string.feature_reports_xlsx_paid),
    balance = stringResource(R.string.feature_reports_xlsx_balance),
    lastPayment = stringResource(R.string.feature_reports_xlsx_last_payment),
    total = stringResource(R.string.feature_reports_xlsx_total),
)

private fun Context.shareFile(file: File, title: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(XLSX_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(Intent.createChooser(send, title))
    } catch (e: ActivityNotFoundException) {
        // No app can receive files; nothing to do.
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun DebtExportScreenPreview() {
    PmTheme {
        DebtExportScreen(
            DebtExportUiState(Company(1, "Alfa Print"), debtorCount = 7, clientCount = 30, totalDebt = Money.ofDram(180_000)),
            onBack = {}, onScopeChange = {}, onSave = {}, onShare = {}, onDismissDialog = {},
        )
    }
}
