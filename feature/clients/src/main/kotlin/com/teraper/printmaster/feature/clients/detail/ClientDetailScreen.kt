package com.teraper.printmaster.feature.clients.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.LocalShowMoney
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmRecordingPlayerSheet
import com.teraper.printmaster.core.designsystem.component.PmRecordingRow
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientAddress
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.Photo
import com.teraper.printmaster.core.model.PhotoOwner
import com.teraper.printmaster.feature.clients.R
import com.teraper.printmaster.feature.clients.common.dial
import com.teraper.printmaster.feature.clients.common.label
import com.teraper.printmaster.feature.clients.common.openMap
import java.time.LocalDate

@Composable
internal fun ClientDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRecordPayment: (Long) -> Unit,
    onAddCharge: (Long) -> Unit,
    onAddPrinter: (clientId: Long) -> Unit,
    onPrinterClick: (clientId: Long, printerId: Long) -> Unit,
    onNewOrder: (clientId: Long) -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    onOpenEntry: (isCharge: Boolean, id: Long) -> Unit,
    viewModel: ClientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ClientDetailEvent.Deleted -> onBack()
            }
        }
    }

    ClientDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = viewModel::onDeleteClick,
        onConfirmDelete = viewModel::onConfirmDelete,
        onDismissDialog = viewModel::onDismissDialog,
        onCall = { context.dial(it) },
        onOpenMap = { context.openMap(it) },
        onTabSelected = viewModel::onTabSelected,
        callActions = CallActions(viewModel::onPlayCall, viewModel::onStopCall, viewModel::onShowAllCalls),
        photoActions = PhotoActions(viewModel::onAddPhoto, viewModel::onDeletePhoto),
        onRecordPayment = onRecordPayment,
        onAddCharge = onAddCharge,
        // Every row opens its own page: full note, file data, move or delete there.
        onEntryClick = { entry -> onOpenEntry(entry is LedgerEntry.Charge, entry.id) },
        onAddPrinter = onAddPrinter,
        onPrinterClick = onPrinterClick,
        onNewOrder = onNewOrder,
        onOrderClick = onOrderClick,
    )
}

@Composable
internal fun ClientDetailScreen(
    state: ClientDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDialog: () -> Unit,
    onCall: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onTabSelected: (ClientTab) -> Unit = {},
    callActions: CallActions = CallActions(),
    photoActions: PhotoActions = PhotoActions(),
    onRecordPayment: (Long) -> Unit = {},
    onAddCharge: (Long) -> Unit = {},
    onEntryClick: (LedgerEntry) -> Unit = {},
    onAddPrinter: (Long) -> Unit = {},
    onPrinterClick: (Long, Long) -> Unit = { _, _ -> },
    onNewOrder: (Long) -> Unit = {},
    onOrderClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(PmTheme.colors.background)) {
        val loaded = state as? ClientDetailUiState.Loaded
        PmTopBar(
            title = loaded?.summary?.client?.name.orEmpty(),
            navigationLabel = stringResource(R.string.feature_clients_back),
            onNavigate = onBack,
            actions = {
                if (loaded != null) {
                    IconButton(onClick = { onEdit(loaded.summary.client.id) }) {
                        Icon(PmIcons.Edit, contentDescription = stringResource(R.string.feature_clients_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_clients_delete))
                    }
                }
            },
        )

        when (state) {
            ClientDetailUiState.Loading -> Unit
            ClientDetailUiState.NotFound -> PmEmptyState(
                icon = PmIcons.Clients,
                title = stringResource(R.string.feature_clients_not_found),
                message = "",
            )
            is ClientDetailUiState.Loaded -> ClientDetailContent(
                state = state,
                onCall = onCall,
                onOpenMap = onOpenMap,
                onTabSelected = onTabSelected,
                callActions = callActions,
                photoActions = photoActions,
                onRecordPayment = { onRecordPayment(state.summary.client.id) },
                onAddCharge = { onAddCharge(state.summary.client.id) },
                onEntryClick = onEntryClick,
                onAddPrinter = { onAddPrinter(state.summary.client.id) },
                onPrinterClick = { onPrinterClick(state.summary.client.id, it) },
                onNewOrder = { onNewOrder(state.summary.client.id) },
                onOrderClick = onOrderClick,
            )
        }
    }

    if (state is ClientDetailUiState.Loaded) {
        when (val dialog = state.dialog) {
            ClientDetailDialog.ConfirmDelete -> PmConfirmDialog(
                title = stringResource(R.string.feature_clients_delete_title),
                message = stringResource(R.string.feature_clients_delete_message, state.summary.client.name),
                confirmText = stringResource(R.string.feature_clients_delete),
                dismissText = stringResource(R.string.feature_clients_cancel),
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDialog,
                destructive = true,
            )
            ClientDetailDialog.DeleteBlocked -> PmMessageDialog(
                title = stringResource(R.string.feature_clients_delete_blocked_title),
                message = stringResource(R.string.feature_clients_delete_blocked_message),
                okText = stringResource(R.string.feature_clients_ok),
                onDismiss = onDismissDialog,
            )
            null -> Unit
        }
        state.playing?.let { recording ->
            PmRecordingPlayerSheet(
                recording = recording,
                missingText = stringResource(R.string.feature_clients_call_missing),
                playLabel = stringResource(R.string.feature_clients_call_play),
                pauseLabel = stringResource(R.string.feature_clients_call_pause),
                onDismiss = callActions.onStop,
            )
        }
    }
}

/** Playing the client's recorded calls. */
internal data class CallActions(
    val onPlay: (CallRecording) -> Unit = {},
    val onStop: () -> Unit = {},
    val onShowAll: () -> Unit = {},
)

/** Adding and removing photos of printers and cartridges. */
internal data class PhotoActions(
    val onAdd: (PhotoOwner, Long, String) -> Unit = { _, _, _ -> },
    val onDelete: (Photo) -> Unit = {},
)

private const val CALLS_SHOWN = 3

@Composable
private fun ClientDetailContent(
    state: ClientDetailUiState.Loaded,
    onCall: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onTabSelected: (ClientTab) -> Unit,
    callActions: CallActions,
    photoActions: PhotoActions,
    onRecordPayment: () -> Unit,
    onAddCharge: () -> Unit,
    onEntryClick: (LedgerEntry) -> Unit,
    onAddPrinter: () -> Unit,
    onPrinterClick: (Long) -> Unit,
    onNewOrder: () -> Unit,
    onOrderClick: (Long) -> Unit,
) {
    val summary = state.summary
    val client = summary.client
    // Another owner's client: what it owes is that owner's business.
    val showMoney = LocalShowMoney.current && !client.isAttached
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        // Header: type, ՀՎՀՀ, quick actions, balance.
        Column(
            modifier = Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = listOfNotNull(
                    client.type.label(),
                    client.taxId?.let { stringResource(R.string.feature_clients_tax_id_value, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuickAction(
                    icon = PmIcons.Call,
                    label = stringResource(R.string.feature_clients_action_call),
                    enabled = client.phones.isNotEmpty(),
                    onClick = { client.phones.firstOrNull()?.let { onCall(it.number) } },
                )
                QuickAction(
                    icon = PmIcons.Map,
                    label = stringResource(R.string.feature_clients_action_map),
                    enabled = client.addresses.isNotEmpty(),
                    onClick = { client.addresses.firstOrNull()?.let { onOpenMap(it.address) } },
                )
                QuickAction(
                    icon = PmIcons.Orders,
                    label = stringResource(R.string.feature_clients_action_order),
                    enabled = true,
                    onClick = onNewOrder,
                )
                if (showMoney) {
                    QuickAction(
                        icon = PmIcons.Payments,
                        label = stringResource(R.string.feature_clients_action_cash),
                        enabled = true,
                        onClick = onRecordPayment,
                    )
                }
            }
            if (showMoney) BalanceBanner(summary.balance)
        }

        val tabs = ClientTab.entries.filter { showMoney || it != ClientTab.FINANCE }
        SecondaryTabRow(
            selectedTabIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
            containerColor = PmTheme.colors.surface,
            contentColor = PmTheme.colors.primary,
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            stringResource(
                                when (tab) {
                                    ClientTab.INFO -> R.string.feature_clients_tab_info
                                    ClientTab.PRINTERS -> R.string.feature_clients_tab_printers
                                    ClientTab.ORDERS -> R.string.feature_clients_tab_orders
                                    ClientTab.FINANCE -> R.string.feature_clients_tab_finance
                                },
                            ),
                            // Four Armenian tab names only fit on one line at this size.
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    selectedContentColor = PmTheme.colors.primary,
                    unselectedContentColor = PmTheme.colors.inkMuted,
                )
            }
        }

        if (state.tab == ClientTab.FINANCE) {
            FinanceSection(summary, state.ledger, onRecordPayment, onAddCharge, onEntryClick)
        } else if (state.tab == ClientTab.PRINTERS) {
            PrintersSection(state.printers, onAddPrinter, onPrinterClick, state.printerPhotos, state.cartridgePhotos, photoActions)
        } else if (state.tab == ClientTab.ORDERS) {
            OrdersSection(state.orders, state.today, onNewOrder, onOrderClick)
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(stringResource(R.string.feature_clients_section_phones))
                ContactCard(
                    rows = client.phones.map { it.number to it.label },
                    emptyText = stringResource(R.string.feature_clients_no_phones),
                    icon = PmIcons.Call,
                    onClick = onCall,
                )
                if (state.calls.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.feature_clients_section_calls, state.calls.size))
                    PmCard(Modifier.fillMaxWidth()) {
                        val shown = if (state.showAllCalls) state.calls else state.calls.take(CALLS_SHOWN)
                        shown.forEachIndexed { index, recording ->
                            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                            PmRecordingRow(recording, onPlay = { callActions.onPlay(recording) })
                        }
                        if (shown.size < state.calls.size) {
                            HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                            TextButton(onClick = callActions.onShowAll, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.feature_clients_calls_show_all, state.calls.size))
                            }
                        }
                    }
                }
                SectionTitle(stringResource(R.string.feature_clients_section_addresses))
                ContactCard(
                    rows = client.addresses.map { it.address to it.label },
                    // A point picked on the map opens exactly there.
                    clickValues = client.addresses.map { it.mapLink ?: it.address },
                    emptyText = stringResource(R.string.feature_clients_no_addresses),
                    icon = PmIcons.Map,
                    onClick = onOpenMap,
                )
                if (client.note.isNotBlank()) {
                    SectionTitle(stringResource(R.string.feature_clients_section_note))
                    PmCard(Modifier.fillMaxWidth()) {
                        Text(client.note, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) PmTheme.colors.primary else PmTheme.colors.outlineStrong
    Column(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PmCard(modifier = Modifier.size(48.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) PmTheme.colors.ink else PmTheme.colors.inkMuted)
    }
}

@Composable
private fun BalanceBanner(balance: Money) {
    if (balance.isZero) return
    val c = PmTheme.colors
    val owes = balance.isPositive
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (owes) c.debtContainer else c.primaryContainer, PmTheme.shapes.button)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (owes) R.string.feature_clients_balance_owes else R.string.feature_clients_balance_overpaid),
            style = MaterialTheme.typography.labelLarge,
            color = if (owes) c.onDebtContainer else c.primary,
        )
        AmountText(
            amount = if (owes) balance else -balance,
            tone = if (owes) AmountTone.Debt else AmountTone.Overpaid,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = PmTheme.colors.inkMuted,
    )
}

@Composable
private fun ContactCard(
    rows: List<Pair<String, String>>,
    emptyText: String,
    icon: ImageVector,
    onClick: (String) -> Unit,
    clickValues: List<String> = rows.map { it.first },
) {
    PmCard(Modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            Text(emptyText, modifier = Modifier.padding(14.dp), color = PmTheme.colors.inkMuted)
        }
        rows.forEachIndexed { index, (value, label) ->
            if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(clickValues[index]) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = PmTheme.colors.ink)
                    if (label.isNotBlank()) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                }
                Icon(icon, contentDescription = null, tint = PmTheme.colors.primary)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ClientDetailScreenPreview() {
    PmTheme {
        ClientDetailScreen(
            state = ClientDetailUiState.Loaded(
                ClientSummary(
                    client = Client(
                        id = 1,
                        name = "«ԱԲԳ Սերվիս» ՍՊԸ",
                        type = ClientType.FIRM,
                        taxId = "01234567",
                        note = "Զանգել հաշվապահին նախքան այցը",
                        phones = listOf(ClientPhone(1, "091 123456", "Հաշվապահ"), ClientPhone(2, "010 123456", "")),
                        addresses = listOf(ClientAddress(1, "Երևան, Կոմիտասի 5, գրասենյակ 12", "")),
                    ),
                    printerCount = 3,
                    charged = Money.ofDram(420_000),
                    paid = Money.ofDram(240_000),
                ),
                tab = ClientTab.FINANCE,
                ledger = listOf(
                    LedgerEntry.Charge(1, LocalDate.of(2026, 10, 2), Money.ofDram(60_000), "", 3, ChargeSource.INVOICE_IMPORT, "0451"),
                    LedgerEntry.Payment(2, LocalDate.of(2026, 9, 28), Money.ofDram(120_000), "", 2, PaymentMethod.BANK, "88231"),
                    LedgerEntry.Payment(3, LocalDate.of(2026, 9, 12), Money.ofDram(35_000), "Այցի ժամանակ", 1, PaymentMethod.CASH, null),
                ),
            ),
            onBack = {}, onEdit = {}, onDelete = {}, onConfirmDelete = {}, onDismissDialog = {},
            onCall = {}, onOpenMap = {},
        )
    }
}
