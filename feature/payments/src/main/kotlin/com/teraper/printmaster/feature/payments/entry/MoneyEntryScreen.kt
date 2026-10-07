package com.teraper.printmaster.feature.payments.entry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.teraper.printmaster.core.designsystem.component.CompanyBadge
import com.teraper.printmaster.core.model.Company
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.DRAM_SIGN
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSearchField
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MoneyEntryDraft
import com.teraper.printmaster.core.model.MoneyEntryError
import com.teraper.printmaster.core.model.MoneyEntryKind
import com.teraper.printmaster.feature.payments.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun MoneyEntryRoute(
    onClose: () -> Unit,
    viewModel: MoneyEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
    val pickerClients by viewModel.pickerClients.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == MoneyEntryEvent.Saved) onClose() }
    }

    MoneyEntryScreen(
        state = state,
        actions = MoneyEntryActions(
            onClose = onClose,
            onKey = viewModel::onKey,
            onUseAmount = viewModel::onUseAmount,
            onNoteChange = viewModel::onNoteChange,
            onOpenClientPicker = viewModel::onOpenClientPicker,
            onOpenDatePicker = viewModel::onOpenDatePicker,
            onDatePicked = viewModel::onDatePicked,
            onSave = viewModel::onSave,
            onCompanySelected = viewModel::onCompanySelected,
        ),
    )

    if (state.form.showClientPicker) {
        ClientPickerSheet(
            query = pickerQuery,
            clients = pickerClients,
            onQueryChange = viewModel::onPickerQueryChange,
            onPick = viewModel::onClientPicked,
            onDismiss = {
                viewModel.onDismissClientPicker()
                // Opened only to pick a client and closed without one: leave the screen.
                if (state.client == null) onClose()
            },
        )
    }
}

internal data class MoneyEntryActions(
    val onClose: () -> Unit = {},
    val onKey: (String) -> Unit = {},
    val onUseAmount: (Money) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    val onOpenClientPicker: () -> Unit = {},
    val onOpenDatePicker: () -> Unit = {},
    val onDatePicked: (LocalDate?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onCompanySelected: (Long) -> Unit = {},
)

@Composable
internal fun MoneyEntryScreen(
    state: MoneyEntryUiState,
    actions: MoneyEntryActions,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val errors = state.form.errors
    Column(modifier = modifier.fillMaxSize().background(PmTheme.colors.background).imePadding()) {
        PmTopBar(
            title = stringResource(if (state.isPayment) R.string.feature_payments_entry_title_payment else R.string.feature_payments_entry_title_charge),
            navigationLabel = stringResource(R.string.feature_payments_close),
            navigationIcon = PmIcons.Close,
            onNavigate = actions.onClose,
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClientField(state.client, showError = MoneyEntryError.CLIENT_REQUIRED in errors, onClick = actions.onOpenClientPicker)

            // Big amount, as typed on the keypad.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (state.isPayment) R.string.feature_payments_amount_received else R.string.feature_payments_amount_owed),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (MoneyEntryError.AMOUNT_REQUIRED in errors) PmTheme.colors.error else PmTheme.colors.inkSecondary,
                )
                Text(
                    text = draft.amount.format(withCurrency = false) + " " + DRAM_SIGN,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, lineHeight = 48.sp, fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (draft.amount.isPositive) PmTheme.colors.ink else PmTheme.colors.outlineStrong,
                    textAlign = TextAlign.Center,
                )
                BalanceAfter(state.balanceAfter)
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.companies.size > 1 && state.company != null) {
                    CompanyChip(state.company, state.companies, actions.onCompanySelected)
                }
                val debt = state.client?.balance
                if (state.isPayment && debt != null && debt.isPositive) {
                    PmFilterChip(
                        text = stringResource(R.string.feature_payments_full_debt, debt.format()),
                        selected = draft.amount == debt,
                        onClick = { actions.onUseAmount(debt) },
                    )
                }
                PmFilterChip(
                    text = if (draft.date == state.today) {
                        stringResource(R.string.feature_payments_date_today, draft.date.formatShort())
                    } else {
                        draft.date.formatShort()
                    },
                    selected = false,
                    onClick = actions.onOpenDatePicker,
                )
            }

            PmTextField(
                value = draft.note,
                onValueChange = actions.onNoteChange,
                label = stringResource(R.string.feature_payments_note),
            )

            Spacer(Modifier.weight(1f))
            Keypad(onKey = actions.onKey)
        }

        Column(Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            PmPrimaryButton(
                text = stringResource(if (state.isPayment) R.string.feature_payments_save_payment else R.string.feature_payments_save_charge),
                onClick = actions.onSave,
                enabled = !state.form.isSaving,
                containerColor = if (state.isPayment) PmTheme.colors.paid else PmTheme.colors.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (state.form.showDatePicker) {
        DateDialog(initial = draft.date, today = state.today, onPicked = actions.onDatePicked)
    }
}

/** Which company the money goes under; tap to pick another. */
@Composable
private fun CompanyChip(company: Company, companies: List<Company>, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(company.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
            leadingIcon = { CompanyBadge(company.initials, company.colorIndex, size = 22.dp) },
            trailingIcon = { Icon(PmIcons.Dropdown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.heightIn(min = 40.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            companies.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    leadingIcon = { CompanyBadge(option.initials, option.colorIndex, size = 26.dp) },
                    trailingIcon = { if (option.id == company.id) Icon(PmIcons.Check, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                )
            }
        }
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (client == null) {
                    Text(
                        stringResource(R.string.feature_payments_choose_client),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (showError) PmTheme.colors.error else PmTheme.colors.primary,
                    )
                } else {
                    Text(client.client.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BalanceLine(client.balance)
                }
            }
            if (client != null) {
                Text(stringResource(R.string.feature_payments_change), style = MaterialTheme.typography.labelLarge, color = PmTheme.colors.primary)
            }
        }
    }
}

/** "Owes 180 000 ֏" / "Overpaid 5 000 ֏" / "No debt". */
@Composable
private fun BalanceLine(balance: Money) {
    val (text, color) = when {
        balance.isPositive -> stringResource(R.string.feature_payments_owes, balance.format()) to PmTheme.colors.debt
        balance.isNegative -> stringResource(R.string.feature_payments_overpaid_by, (-balance).format()) to PmTheme.colors.primary
        else -> stringResource(R.string.feature_payments_no_debt) to PmTheme.colors.paid
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
}

@Composable
private fun BalanceAfter(after: Money?) {
    if (after == null) return
    val text = when {
        after.isPositive -> stringResource(R.string.feature_payments_debt_after, after.format())
        after.isNegative -> stringResource(R.string.feature_payments_overpaid_after, (-after).format())
        else -> stringResource(R.string.feature_payments_settled_after)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
}

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", MoneyEntryDraft.KEY_DELETE)

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val deleteLabel = stringResource(R.string.feature_payments_delete_digit)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    PmCard(
                        modifier = Modifier.weight(1f).height(52.dp),
                        onClick = { onKey(key) },
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            if (key == MoneyEntryDraft.KEY_DELETE) {
                                Icon(PmIcons.Backspace, contentDescription = deleteLabel)
                            } else {
                                Text(key, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, today: LocalDate, onPicked: (LocalDate?) -> Unit) {
    // The Material date picker works in UTC milliseconds.
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.utcMillis(),
        selectableDates = object : SelectableDates {
            // Money can't be received in the future.
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.utcMillis()
        },
    )
    DatePickerDialog(
        onDismissRequest = { onPicked(null) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text(stringResource(R.string.feature_payments_ok)) }
        },
        dismissButton = { TextButton(onClick = { onPicked(null) }) { Text(stringResource(R.string.feature_payments_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientPickerSheet(
    query: String,
    clients: List<ClientSummary>,
    onQueryChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.feature_payments_choose_client), style = MaterialTheme.typography.titleLarge)
            PmSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.feature_payments_search_hint),
                clearLabel = stringResource(R.string.feature_payments_clear_search),
            )
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(clients, key = { it.client.id }) { summary ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(summary.client.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(summary.client.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (summary.balance.isPositive) {
                        AmountText(summary.balance, tone = AmountTone.Debt, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun MoneyEntryPreview() {
    val today = LocalDate.of(2026, 10, 7)
    PmTheme {
        MoneyEntryScreen(
            state = MoneyEntryUiState(
                form = MoneyEntryForm(MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, clientId = 1, amountDigits = "17000", date = today)),
                today = today,
                company = Company(1, "«Ալֆա» ՍՊԸ", colorIndex = 0),
                companies = listOf(Company(1, "«Ալֆա» ՍՊԸ", colorIndex = 0), Company(2, "Beta Print", colorIndex = 3)),
                client = ClientSummary(
                    client = Client(1, "«ԱԲԳ Սերվիս» ՍՊԸ", ClientType.FIRM, null, "", emptyList(), emptyList()),
                    charged = Money.ofDram(420_000),
                    paid = Money.ofDram(240_000),
                ),
            ),
            actions = MoneyEntryActions(),
        )
    }
}
