package com.teraper.printmaster.feature.settings.backup

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.BackupSummary
import com.teraper.printmaster.feature.settings.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Backups are zips (database + photos); restore accepts any file type, for older .db backups too. */
private const val BACKUP_MIME = "application/zip"

@Composable
internal fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) {
        viewModel.onSaveTo(it?.toString())
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onRestoreFrom(it?.toString())
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BackupEvent.RestartApp -> context.restartApp()
            }
        }
    }
    BackupScreen(
        state = state,
        onBack = onBack,
        onSave = { saveLauncher.launch(viewModel.suggestedFileName()) },
        onRestore = { restoreLauncher.launch(arrayOf("*/*")) },
        onConfirmRestore = viewModel::onConfirmRestore,
        onDismissDialog = viewModel::onDismissDialog,
    )
}

@Composable
internal fun BackupScreen(
    state: BackupUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state.work != null
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_settings_backup_title),
            navigationLabel = stringResource(R.string.feature_settings_back),
            onNavigate = onBack,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LastBackupCard(state)
            Text(
                stringResource(R.string.feature_settings_backup_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkSecondary,
            )
            PmPrimaryButton(
                text = stringResource(R.string.feature_settings_backup_save),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                icon = PmIcons.Backup,
                enabled = !busy,
            )
            Text(
                stringResource(R.string.feature_settings_backup_save_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = PmTheme.colors.outline)
            Text(stringResource(R.string.feature_settings_restore_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.feature_settings_restore_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkSecondary,
            )
            PmSecondaryButton(
                text = stringResource(R.string.feature_settings_restore_pick),
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth(),
                icon = PmIcons.Restore,
                enabled = !busy,
            )
            state.work?.let { WorkInProgress(it) }
        }
    }

    when (val dialog = state.dialog) {
        null -> Unit
        BackupDialog.Saved -> PmMessageDialog(
            title = stringResource(R.string.feature_settings_backup_saved_title),
            message = stringResource(R.string.feature_settings_backup_saved_message),
            okText = stringResource(R.string.feature_settings_ok),
            onDismiss = onDismissDialog,
        )
        BackupDialog.SaveFailed -> PmMessageDialog(
            title = stringResource(R.string.feature_settings_backup_failed_title),
            message = stringResource(R.string.feature_settings_backup_failed_message),
            okText = stringResource(R.string.feature_settings_ok),
            onDismiss = onDismissDialog,
        )
        BackupDialog.NotABackup -> PmMessageDialog(
            title = stringResource(R.string.feature_settings_restore_not_backup_title),
            message = stringResource(R.string.feature_settings_restore_not_backup_message),
            okText = stringResource(R.string.feature_settings_ok),
            onDismiss = onDismissDialog,
        )
        BackupDialog.FromNewerApp -> PmMessageDialog(
            title = stringResource(R.string.feature_settings_restore_newer_title),
            message = stringResource(R.string.feature_settings_restore_newer_message),
            okText = stringResource(R.string.feature_settings_ok),
            onDismiss = onDismissDialog,
        )
        is BackupDialog.ConfirmRestore -> ConfirmRestoreDialog(dialog, onConfirmRestore, onDismissDialog)
    }
}

@Composable
private fun LastBackupCard(state: BackupUiState) {
    PmCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(
                    if (state.needsBackup) PmTheme.colors.warningContainer else PmTheme.colors.paidContainer,
                    PmTheme.shapes.button,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.needsBackup) PmIcons.Warning else PmIcons.Check,
                    contentDescription = null,
                    tint = if (state.needsBackup) PmTheme.colors.warning else PmTheme.colors.paid,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.feature_settings_backup_last),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
                Text(
                    state.lastBackup?.formatDateTime() ?: stringResource(R.string.feature_settings_backup_never),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.needsBackup) PmTag(stringResource(R.string.feature_settings_backup_needed), TagTone.Warning)
        }
    }
}

@Composable
private fun WorkInProgress(work: BackupWork) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = PmTheme.colors.primary, strokeWidth = 3.dp)
        Text(
            stringResource(
                when (work) {
                    BackupWork.SAVING -> R.string.feature_settings_backup_saving
                    BackupWork.CHECKING -> R.string.feature_settings_restore_checking
                    BackupWork.RESTORING -> R.string.feature_settings_restore_restoring
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Shows what is in the file next to what is on the phone, so the user doesn't restore an old one by mistake. */
@Composable
private fun ConfirmRestoreDialog(dialog: BackupDialog.ConfirmRestore, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_restore_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.feature_settings_restore_confirm_message))
                CompareRow("", stringResource(R.string.feature_settings_restore_file), stringResource(R.string.feature_settings_restore_phone), header = true)
                CompareRow(stringResource(R.string.feature_settings_restore_companies), dialog.backup.companies.size.toString(), dialog.current.companies.size.toString())
                CompareRow(stringResource(R.string.feature_settings_restore_clients), dialog.backup.clients.toString(), dialog.current.clients.toString())
                CompareRow(stringResource(R.string.feature_settings_restore_orders), dialog.backup.orders.toString(), dialog.current.orders.toString())
                CompareRow(stringResource(R.string.feature_settings_restore_money), dialog.backup.moneyEntries.toString(), dialog.current.moneyEntries.toString())
                CompareRow(stringResource(R.string.feature_settings_restore_last_change), dialog.backup.lastChange.label(), dialog.current.lastChange.label())
                if (dialog.backup.companies.isNotEmpty()) {
                    Text(
                        dialog.backup.companies.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = PmTheme.colors.inkMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feature_settings_restore_confirm), color = PmTheme.colors.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_cancel)) } },
        containerColor = PmTheme.colors.surface,
    )
}

@Composable
private fun CompareRow(label: String, file: String, phone: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.4f), style = style, color = PmTheme.colors.inkSecondary)
        Text(file, Modifier.weight(1f), style = style, fontWeight = weight)
        Text(phone, Modifier.weight(1f), style = style, fontWeight = weight, color = PmTheme.colors.inkSecondary)
    }
}

@Composable
private fun LocalDate?.label(): String = this?.formatShort() ?: "—"

private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

private fun Instant.formatDateTime(): String = atZone(ZoneId.systemDefault()).format(DATE_TIME)

/** The closed database can't be reopened in this process, so start a fresh one. */
private fun Context.restartApp() {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun BackupScreenPreview() {
    PmTheme {
        BackupScreen(
            BackupUiState(
                lastBackup = Instant.parse("2026-10-01T10:00:00Z"),
                needsBackup = true,
                dialog = BackupDialog.ConfirmRestore(
                    BackupSummary(listOf("Alfa"), 12, 40, 80, LocalDate.of(2026, 10, 1)),
                    BackupSummary(listOf("Alfa"), 14, 46, 92, LocalDate.of(2026, 10, 9)),
                ),
            ),
            onBack = {}, onSave = {}, onRestore = {}, onConfirmRestore = {}, onDismissDialog = {},
        )
    }
}
