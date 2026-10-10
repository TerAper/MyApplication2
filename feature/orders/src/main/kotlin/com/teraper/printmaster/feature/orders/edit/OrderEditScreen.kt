package com.teraper.printmaster.feature.orders.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmClientPickerSheet
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.component.formatTime
import com.teraper.printmaster.core.designsystem.component.relativeLabel
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientAddress
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderDraftError
import com.teraper.printmaster.feature.orders.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
internal fun OrderEditRoute(
    onClose: () -> Unit,
    onSaved: (orderId: Long, wasNew: Boolean) -> Unit,
    viewModel: OrderEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
    val pickerClients by viewModel.pickerClients.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OrderEditEvent.Saved -> onSaved(event.orderId, event.wasNew)
                OrderEditEvent.Close -> onClose()
            }
        }
    }
    BackHandler(onBack = viewModel::onCloseRequest)

    OrderEditScreen(
        state = state,
        actions = OrderEditActions(
            onClose = viewModel::onCloseRequest,
            onOpenClientPicker = viewModel::onOpenClientPicker,
            onDateChange = viewModel::onDateChange,
            onOpenDatePicker = viewModel::onOpenDatePicker,
            onDatePicked = viewModel::onDatePicked,
            onTimeChange = viewModel::onTimeChange,
            onOpenTimePicker = viewModel::onOpenTimePicker,
            onTimePicked = viewModel::onTimePicked,
            onAddressChange = viewModel::onAddressChange,
            onPhoneChange = viewModel::onPhoneChange,
            onMasterChange = viewModel::onMasterChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onSave = viewModel::onSave,
            onDiscardConfirmed = viewModel::onDiscardConfirmed,
            onDiscardDismissed = viewModel::onDiscardDismissed,
        ),
    )

    if (state.form.showClientPicker) {
        PmClientPickerSheet(
            title = stringResource(R.string.feature_orders_choose_client),
            searchHint = stringResource(R.string.feature_orders_search_client),
            clearLabel = stringResource(R.string.feature_orders_clear),
            query = pickerQuery,
            clients = pickerClients,
            onQueryChange = viewModel::onPickerQueryChange,
            onPick = viewModel::onClientPicked,
            onDismiss = {
                viewModel.onDismissClientPicker()
                // Opened only to pick a client and closed without one: leave the form.
                if (state.client == null && state.isNew) onClose()
            },
        )
    }
}

internal data class OrderEditActions(
    val onClose: () -> Unit = {},
    val onOpenClientPicker: () -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onOpenDatePicker: () -> Unit = {},
    val onDatePicked: (LocalDate?) -> Unit = {},
    val onTimeChange: (LocalTime) -> Unit = {},
    val onOpenTimePicker: () -> Unit = {},
    val onTimePicked: (LocalTime?) -> Unit = {},
    val onAddressChange: (Long?) -> Unit = {},
    val onPhoneChange: (Long?) -> Unit = {},
    val onMasterChange: (Long?) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDiscardConfirmed: () -> Unit = {},
    val onDiscardDismissed: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderEditScreen(state: OrderEditUiState, actions: OrderEditActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    val client = state.client?.client
    Column(modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isNew) R.string.feature_orders_new else R.string.feature_orders_edit),
            navigationLabel = stringResource(R.string.feature_orders_cancel),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
            actions = {
                val company = state.company
                if (company != null) CompanyBadge(company.initials, company.colorIndex, Modifier.padding(end = 16.dp), size = 28.dp)
            },
        )
        if (state.form.isLoading) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label(stringResource(R.string.feature_orders_client))
            ClientField(state.client, showError = OrderDraftError.CLIENT_REQUIRED in state.form.errors, onClick = actions.onOpenClientPicker)

            Label(stringResource(R.string.feature_orders_day))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tomorrow = state.today.plusDays(1)
                PmFilterChip(stringResource(R.string.feature_orders_today), draft.date == state.today, { actions.onDateChange(state.today) })
                PmFilterChip(stringResource(R.string.feature_orders_tomorrow), draft.date == tomorrow, { actions.onDateChange(tomorrow) })
                val other = draft.date != state.today && draft.date != tomorrow
                PmFilterChip(
                    if (other) draft.date.relativeLabel(state.today) else stringResource(R.string.feature_orders_pick_date),
                    other,
                    actions.onOpenDatePicker,
                )
            }

            Label(stringResource(R.string.feature_orders_time))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderDraft.QUICK_TIMES.forEach { time ->
                    PmFilterChip(time.formatTime(), draft.time == time, { actions.onTimeChange(time) })
                }
                val custom = draft.time !in OrderDraft.QUICK_TIMES
                PmFilterChip(
                    if (custom) draft.time.formatTime() else stringResource(R.string.feature_orders_other_time),
                    custom,
                    actions.onOpenTimePicker,
                )
            }

            if (client != null && client.addresses.isNotEmpty()) {
                Label(stringResource(R.string.feature_orders_address))
                Choices(
                    options = client.addresses.map { it.id to listOf(it.address, it.label).filter(String::isNotBlank).joinToString(" · ") },
                    selected = draft.addressId,
                    onSelect = actions.onAddressChange,
                )
            }
            if (client != null && client.phones.isNotEmpty()) {
                Label(stringResource(R.string.feature_orders_phone))
                Choices(
                    options = client.phones.map { it.id to listOf(it.number, it.label).filter(String::isNotBlank).joinToString(" · ") },
                    selected = draft.phoneId,
                    onSelect = actions.onPhoneChange,
                )
            }
            if (state.masters.isNotEmpty()) {
                Label(stringResource(R.string.feature_orders_master))
                Choices(
                    options = state.masters.map { it.id to it.name },
                    selected = draft.masterId,
                    onSelect = actions.onMasterChange,
                    noneLabel = stringResource(R.string.feature_orders_not_assigned),
                )
            }

            Label(stringResource(R.string.feature_orders_what))
            PmTextField(
                value = draft.description,
                onValueChange = actions.onDescriptionChange,
                label = stringResource(R.string.feature_orders_what_hint),
                error = if (OrderDraftError.DESCRIPTION_REQUIRED in state.form.errors) stringResource(R.string.feature_orders_error_what) else null,
                capitalization = KeyboardCapitalization.Sentences,
                singleLine = false,
                minLines = 2,
            )
        }

        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(
                text = if (state.isNew) {
                    stringResource(R.string.feature_orders_create, "${draft.date.relativeLabel(state.today)} ${draft.time.formatTime()}")
                } else {
                    stringResource(R.string.feature_orders_save)
                },
                onClick = actions.onSave,
                enabled = !state.form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (state.form.showDatePicker) DateDialog(draft.date, actions.onDatePicked)
    if (state.form.showTimePicker) TimeDialog(draft.time, actions.onTimePicked)
    if (state.form.showDiscardDialog) {
        PmConfirmDialog(
            title = stringResource(R.string.feature_orders_discard_title),
            message = null,
            confirmText = stringResource(R.string.feature_orders_discard),
            dismissText = stringResource(R.string.feature_orders_keep_editing),
            onConfirm = actions.onDiscardConfirmed,
            onDismiss = actions.onDiscardDismissed,
            destructive = true,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, modifier = Modifier.padding(top = 6.dp, start = 2.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

/** One choice out of a few, as chips; [noneLabel] adds a "none" choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(options: List<Pair<Long, String>>, selected: Long?, onSelect: (Long?) -> Unit, noneLabel: String? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, text) -> PmFilterChip(text, selected == id, { onSelect(id) }) }
        if (noneLabel != null) PmFilterChip(noneLabel, selected == null, { onSelect(null) })
    }
}

@Composable
private fun ClientField(client: ClientSummary?, showError: Boolean, onClick: () -> Unit) {
    PmCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        borderColor = if (showError) PmTheme.colors.error else PmTheme.colors.outlineStrong,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (client == null) {
                    Text(
                        stringResource(R.string.feature_orders_choose_client),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (showError) PmTheme.colors.error else PmTheme.colors.primary,
                    )
                } else {
                    Text(client.client.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (client.balance.isPositive) {
                        Text(
                            stringResource(R.string.feature_orders_owes, client.balance.format()),
                            style = MaterialTheme.typography.bodySmall,
                            color = PmTheme.colors.debt,
                        )
                    }
                }
            }
            if (client != null) {
                Text(stringResource(R.string.feature_orders_change), style = MaterialTheme.typography.labelLarge, color = PmTheme.colors.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onPicked: (LocalDate?) -> Unit) {
    // The Material date picker works in UTC milliseconds.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = { onPicked(null) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text(stringResource(R.string.feature_orders_ok)) }
        },
        dismissButton = { TextButton(onClick = { onPicked(null) }) { Text(stringResource(R.string.feature_orders_cancel)) } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onPicked: (LocalTime?) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = { onPicked(null) },
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.feature_orders_ok)) } },
        dismissButton = { TextButton(onClick = { onPicked(null) }) { Text(stringResource(R.string.feature_orders_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun OrderEditPreview() {
    val today = LocalDate.of(2026, 10, 8)
    val client = Client(
        1, "«ԱԲԳ Սերվիս» ՍՊԸ", ClientType.FIRM, "01234567", "",
        phones = listOf(ClientPhone(1, "091 123456", "Հաշվապահ")),
        addresses = listOf(ClientAddress(1, "Կոմիտաս 5", ""), ClientAddress(2, "Արշակունյաց 12", "Պահեստ")),
    )
    PmTheme {
        OrderEditScreen(
            state = OrderEditUiState(
                form = OrderForm(draft = OrderDraft(clientId = 1, date = today.plusDays(1), addressId = 1, phoneId = 1, description = "Լիցքավորել 2 քարտրիջ")),
                today = today,
                client = ClientSummary(client, charged = Money.ofDram(180_000)),
                masters = listOf(Master(1, "Վարդան"), Master(2, "Արամ")),
            ),
            actions = OrderEditActions(),
        )
    }
}
