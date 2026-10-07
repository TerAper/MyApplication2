package com.teraper.printmaster.feature.account.companies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.feature.account.R
import com.teraper.printmaster.feature.account.common.CompanyFields

@Composable
internal fun CompanyEditRoute(onClose: () -> Unit, viewModel: CompanyEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CompanyEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)
    CompanyEditScreen(
        state = state,
        onClose = viewModel::onCloseRequest,
        onChange = viewModel::onChange,
        onSave = viewModel::onSave,
        onMakeDefault = viewModel::onMakeDefault,
        onDelete = viewModel::onDeleteClick,
        onConfirmDelete = viewModel::onConfirmDelete,
        onDiscardConfirmed = viewModel::onDiscardConfirmed,
        onDismissDialog = viewModel::onDismissDialog,
    )
}

@Composable
internal fun CompanyEditScreen(
    state: CompanyEditUiState,
    onClose: () -> Unit,
    onChange: (CompanyDraft) -> Unit,
    onSave: () -> Unit,
    onMakeDefault: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDiscardConfirmed: () -> Unit,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_account_new_company else R.string.feature_account_company),
            navigationLabel = stringResource(R.string.feature_account_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = onClose,
            actions = {
                if (state.canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_account_delete))
                    }
                }
            },
        )
        if (state.isLoading) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CompanyFields(state.draft, state.errors, state.taxIdTakenBy, onChange)
            if (!state.isNew && state.canDelete) {
                if (state.isDefault) {
                    Text(
                        stringResource(R.string.feature_account_is_default),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.inkMuted,
                    )
                } else {
                    PmSecondaryButton(
                        text = stringResource(R.string.feature_account_make_default),
                        onClick = onMakeDefault,
                        icon = PmIcons.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(
                text = stringResource(R.string.feature_account_save),
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (state.dialog) {
        CompanyEditDialog.DISCARD -> PmConfirmDialog(
            title = stringResource(R.string.feature_account_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_account_discard),
            dismissText = stringResource(R.string.feature_account_keep_editing),
            onConfirm = onDiscardConfirmed,
            onDismiss = onDismissDialog,
            destructive = true,
        )
        CompanyEditDialog.CONFIRM_DELETE -> PmConfirmDialog(
            title = stringResource(R.string.feature_account_delete_company_title),
            message = state.draft.name,
            confirmText = stringResource(R.string.feature_account_delete),
            dismissText = stringResource(R.string.feature_account_cancel),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDialog,
            destructive = true,
        )
        CompanyEditDialog.DELETE_DEFAULT -> PmMessageDialog(
            title = stringResource(R.string.feature_account_cant_delete),
            message = stringResource(R.string.feature_account_cant_delete_default),
            okText = stringResource(R.string.feature_account_ok),
            onDismiss = onDismissDialog,
        )
        CompanyEditDialog.DELETE_HAS_RECORDS -> PmMessageDialog(
            title = stringResource(R.string.feature_account_cant_delete),
            message = stringResource(R.string.feature_account_cant_delete_records),
            okText = stringResource(R.string.feature_account_ok),
            onDismiss = onDismissDialog,
        )
        null -> Unit
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun CompanyEditPreview() {
    PmTheme {
        CompanyEditScreen(
            CompanyEditUiState(draft = CompanyDraft(id = 2, name = "Beta Print", taxId = "01234567", colorIndex = 3), canDelete = true),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
