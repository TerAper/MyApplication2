package com.teraper.printmaster.feature.account.masters

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.feature.account.R

@Composable
internal fun MastersRoute(onBack: () -> Unit, viewModel: MastersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MastersScreen(
        state = state,
        onBack = onBack,
        onAdd = viewModel::onAdd,
        onEdit = viewModel::onEdit,
        onFormChange = viewModel::onFormChange,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        onDismiss = viewModel::onDismiss,
    )
}

@Composable
internal fun MastersScreen(
    state: MastersUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Master) -> Unit,
    onFormChange: (MasterForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_account_masters),
            navigationLabel = stringResource(R.string.feature_account_back),
            onNavigate = onBack,
        )
        if (state.isLoading) return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.masters.isEmpty()) {
                Text(
                    stringResource(R.string.feature_account_no_masters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PmTheme.colors.inkMuted,
                )
            } else {
                PmCard(Modifier.fillMaxWidth()) {
                    state.masters.forEachIndexed { index, master ->
                        if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        Row(
                            Modifier.fillMaxWidth().clickable { onEdit(master) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PmIcons.Master, contentDescription = null, tint = PmTheme.colors.primary)
                            Column(Modifier.weight(1f)) {
                                Text(master.name, style = MaterialTheme.typography.bodyLarge)
                                if (master.phone.isNotBlank()) {
                                    Text(master.phone, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                                }
                            }
                            Icon(PmIcons.Edit, contentDescription = null, tint = PmTheme.colors.outlineStrong)
                        }
                    }
                }
            }
            PmSecondaryButton(
                text = stringResource(R.string.feature_account_add_master),
                onClick = onAdd,
                icon = PmIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.form?.let { form ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (form.id == 0L) R.string.feature_account_add_master else R.string.feature_account_master)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PmTextField(
                        value = form.name,
                        onValueChange = { onFormChange(form.copy(name = it)) },
                        label = stringResource(R.string.feature_account_master_name),
                        error = if (form.nameMissing) stringResource(R.string.feature_account_error_name) else null,
                        capitalization = KeyboardCapitalization.Words,
                    )
                    PmTextField(
                        value = form.phone,
                        onValueChange = { onFormChange(form.copy(phone = it)) },
                        label = stringResource(R.string.feature_account_master_phone),
                        keyboardType = KeyboardType.Phone,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.feature_account_save)) } },
            dismissButton = {
                Row {
                    if (form.id != 0L) {
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.feature_account_delete), color = PmTheme.colors.debt) }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_account_cancel)) }
                }
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 600)
@Composable
private fun MastersPreview() {
    PmTheme {
        MastersScreen(
            MastersUiState(isLoading = false, masters = listOf(Master(1, "Վարդան", "091 123456"), Master(2, "Արամ"))),
            {}, {}, {}, {}, {}, {}, {},
        )
    }
}
