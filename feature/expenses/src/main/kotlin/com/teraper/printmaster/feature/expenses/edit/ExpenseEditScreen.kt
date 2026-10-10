package com.teraper.printmaster.feature.expenses.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PhotoStripLabels
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmPhotoStrip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ExpenseCategory
import com.teraper.printmaster.core.model.ExpenseDraft
import com.teraper.printmaster.core.model.ExpenseError
import com.teraper.printmaster.feature.expenses.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun ExpenseEditRoute(onClose: () -> Unit, viewModel: ExpenseEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ExpenseEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = onClose)
    ExpenseEditScreen(
        state = state,
        isNew = viewModel.isNew,
        actions = ExpenseEditActions(
            onClose = onClose,
            onAmountChange = viewModel::onAmountChange,
            onCategoryChange = viewModel::onCategoryChange,
            onDateChange = viewModel::onDateChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDeleteClick,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onAddPhoto = viewModel::onAddPhoto,
            onDeletePhoto = viewModel::onDeletePhoto,
        ),
    )
}

internal data class ExpenseEditActions(
    val onClose: () -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onCategoryChange: (ExpenseCategory) -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onAddPhoto: (String) -> Unit = {},
    val onDeletePhoto: (com.teraper.printmaster.core.model.Photo) -> Unit = {},
)

@Composable
internal fun ExpenseEditScreen(state: ExpenseEditUiState, isNew: Boolean, actions: ExpenseEditActions) {
    val form = state.form
    val draft = form.draft
    var pickingDate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (isNew) R.string.feature_expenses_new else R.string.feature_expenses_edit),
            navigationLabel = stringResource(R.string.feature_expenses_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
            actions = {
                if (!isNew) IconButton(onClick = actions.onDelete) { Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_expenses_delete)) }
            },
        )
        if (form.isLoading) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PmTextField(
                value = draft.amountDigits,
                onValueChange = actions.onAmountChange,
                label = stringResource(R.string.feature_expenses_amount),
                placeholder = "0 ֏",
                error = if (ExpenseError.AMOUNT_REQUIRED in form.errors) stringResource(R.string.feature_expenses_error_amount) else null,
                keyboardType = KeyboardType.Number,
            )
            SectionTitle(stringResource(R.string.feature_expenses_category))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { category ->
                    PmFilterChip(text = category.label(), selected = draft.category == category, onClick = { actions.onCategoryChange(category) })
                }
            }
            SectionTitle(stringResource(R.string.feature_expenses_day))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PmFilterChip(stringResource(R.string.feature_expenses_today), draft.date == state.today, { actions.onDateChange(state.today) })
                PmFilterChip(stringResource(R.string.feature_expenses_yesterday), draft.date == state.today.minusDays(1), { actions.onDateChange(state.today.minusDays(1)) })
                val other = draft.date != state.today && draft.date != state.today.minusDays(1)
                PmFilterChip(if (other) draft.date.formatShort() else stringResource(R.string.feature_expenses_pick_date), other, { pickingDate = true })
            }
            PmTextField(
                value = draft.note,
                onValueChange = actions.onNoteChange,
                label = stringResource(R.string.feature_expenses_note),
                placeholder = stringResource(R.string.feature_expenses_note_hint),
                singleLine = false,
                minLines = 2,
            )
            SectionTitle(stringResource(R.string.feature_expenses_receipt))
            if (isNew) {
                Text(stringResource(R.string.feature_expenses_receipt_after_save), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            } else {
                PmPhotoStrip(
                    photos = state.photos,
                    labels = PhotoStripLabels(
                        add = stringResource(R.string.feature_expenses_photo_add),
                        camera = stringResource(R.string.feature_expenses_photo_camera),
                        gallery = stringResource(R.string.feature_expenses_photo_gallery),
                        delete = stringResource(R.string.feature_expenses_photo_delete),
                        close = stringResource(R.string.feature_expenses_photo_close),
                    ),
                    onAdd = actions.onAddPhoto,
                    onDelete = actions.onDeletePhoto,
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(stringResource(R.string.feature_expenses_save), actions.onSave, Modifier.fillMaxWidth(), enabled = !form.isSaving)
        }
    }
    if (pickingDate) {
        DatePick(draft.date) { picked ->
            pickingDate = false
            picked?.let(actions.onDateChange)
        }
    }
    if (form.confirmDelete) {
        PmConfirmDialog(
            title = stringResource(R.string.feature_expenses_delete_title),
            message = null,
            confirmText = stringResource(R.string.feature_expenses_delete),
            dismissText = stringResource(R.string.feature_expenses_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDelete,
            destructive = true,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePick(initial: LocalDate, onPicked: (LocalDate?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { onPicked(null) },
        confirmButton = {
            TextButton(onClick = { onPicked(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }) }) {
                Text(stringResource(R.string.feature_expenses_ok))
            }
        },
        dismissButton = { TextButton(onClick = { onPicked(null) }) { Text(stringResource(R.string.feature_expenses_cancel)) } },
    ) { DatePicker(state = state) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ExpenseEditPreview() {
    val today = LocalDate.of(2026, 10, 10)
    PmTheme {
        ExpenseEditScreen(
            ExpenseEditUiState(ExpenseForm(isLoading = false, draft = ExpenseDraft(amountDigits = "4000", date = today, note = "Fuel")), today),
            isNew = true,
            actions = ExpenseEditActions(),
        )
    }
}
