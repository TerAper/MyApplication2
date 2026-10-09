package com.teraper.printmaster.feature.clients.entry

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmClientPickerSheet
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.EntryKind
import com.teraper.printmaster.core.model.ImportedEntryDetail
import com.teraper.printmaster.core.model.MatchReason
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PaymentMatchState
import com.teraper.printmaster.core.model.RelatedEntry
import com.teraper.printmaster.feature.clients.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val IMPORTED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

@Composable
internal fun EntryDetailRoute(
    onBack: () -> Unit,
    onOpenEntry: (isCharge: Boolean, id: Long) -> Unit,
    onOpenOrder: (orderId: Long) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EntryDetailEvent.Deleted -> onBack()
            }
        }
    }
    EntryDetailScreen(
        state = state,
        actions = EntryDetailActions(
            onBack = onBack,
            onOpenEntry = onOpenEntry,
            onOpenOrder = onOpenOrder,
            onDelete = viewModel::onDeleteClick,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
            onMove = viewModel::onMoveClick,
            onPickQueryChange = viewModel::onPickQueryChange,
            onClientPicked = viewModel::onClientPicked,
            onDismissPick = viewModel::onDismissPick,
            onDetach = viewModel::onDetachClick,
            onConfirmDetach = viewModel::onConfirmDetach,
            onDismissDetach = viewModel::onDismissDetach,
        ),
    )
}

internal data class EntryDetailActions(
    val onBack: () -> Unit = {},
    val onOpenEntry: (Boolean, Long) -> Unit = { _, _ -> },
    val onOpenOrder: (Long) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onMove: () -> Unit = {},
    val onPickQueryChange: (String) -> Unit = {},
    val onClientPicked: (Long) -> Unit = {},
    val onDismissPick: () -> Unit = {},
    val onDetach: () -> Unit = {},
    val onConfirmDetach: () -> Unit = {},
    val onDismissDetach: () -> Unit = {},
)

@Composable
internal fun EntryDetailScreen(state: EntryDetailUiState, actions: EntryDetailActions) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = (state as? EntryDetailUiState.Loaded)?.detail?.kind?.title().orEmpty(),
            navigationLabel = stringResource(R.string.feature_clients_back),
            onNavigate = actions.onBack,
        )
        when (state) {
            EntryDetailUiState.Loading -> Unit
            EntryDetailUiState.NotFound -> PmEmptyState(
                icon = PmIcons.Search,
                title = stringResource(R.string.feature_clients_entry_gone),
                message = stringResource(R.string.feature_clients_entry_gone_message),
            )
            is EntryDetailUiState.Loaded -> Content(state.detail, actions)
        }
    }
    if (state is EntryDetailUiState.Loaded) {
        state.pickQuery?.let { query ->
            PmClientPickerSheet(
                title = stringResource(R.string.feature_clients_entry_whose, state.detail.amount.format()),
                searchHint = stringResource(R.string.feature_clients_search_hint),
                clearLabel = stringResource(R.string.feature_clients_clear_search),
                query = query,
                clients = state.pickClients,
                onQueryChange = actions.onPickQueryChange,
                onPick = actions.onClientPicked,
                onDismiss = actions.onDismissPick,
            )
        }
        if (state.confirmDelete) {
            PmConfirmDialog(
                title = stringResource(R.string.feature_clients_delete_entry_title),
                message = "${state.detail.kind.title()} · ${state.detail.date.formatShort()} · ${state.detail.amount.format()}",
                confirmText = stringResource(R.string.feature_clients_delete),
                dismissText = stringResource(R.string.feature_clients_cancel),
                onConfirm = actions.onConfirmDelete,
                onDismiss = actions.onDismissDelete,
                destructive = true,
            )
        }
        if (state.confirmDetach) {
            PmConfirmDialog(
                title = stringResource(R.string.feature_clients_entry_detach_title, state.detail.clientName.orEmpty()),
                message = stringResource(R.string.feature_clients_entry_detach_message),
                confirmText = stringResource(R.string.feature_clients_entry_detach),
                dismissText = stringResource(R.string.feature_clients_cancel),
                onConfirm = actions.onConfirmDetach,
                onDismiss = actions.onDismissDetach,
                destructive = true,
            )
        }
    }
}

@Composable
private fun EntryKind.title(): String = stringResource(
    when (this) {
        EntryKind.INVOICE -> R.string.feature_clients_entry_invoice
        EntryKind.BANK_PAYMENT -> R.string.feature_clients_entry_payment
        EntryKind.CASH_PAYMENT -> R.string.feature_clients_entry_cash
        EntryKind.MANUAL_CHARGE -> R.string.feature_clients_entry_manual
        EntryKind.REPAIR_CHARGE -> R.string.feature_clients_entry_repair
    },
)

@Composable
private fun Content(detail: ImportedEntryDetail, actions: EntryDetailActions) {
    val isInvoice = detail.kind == EntryKind.INVOICE
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PmCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AmountText(detail.amount, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(detail.date.formatShort(), detail.documentNumber?.let { "№ $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PmTheme.colors.inkSecondary,
                )
                Text(
                    detail.clientName?.let { stringResource(R.string.feature_clients_entry_client, it) }
                        ?: stringResource(R.string.feature_clients_entry_no_client),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (detail.clientName == null) PmTheme.colors.warning else PmTheme.colors.ink,
                )
                if (detail.kind == EntryKind.BANK_PAYMENT) MatchTag(detail)
            }
        }

        // What was typed (cash, debt) or done (repair), in full: the list only shows one line.
        if (!detail.isImported && detail.note.isNotBlank()) {
            SectionTitle(stringResource(R.string.feature_clients_entry_note))
            PmCard(Modifier.fillMaxWidth()) {
                Text(detail.note, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (!detail.isImported) {
            detail.createdAt?.let {
                Text(stringResource(R.string.feature_clients_entry_entered, it.format(IMPORTED_AT)), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
            }
        }

        detail.orderId?.let { orderId ->
            PmSecondaryButton(
                text = stringResource(R.string.feature_clients_entry_open_order),
                onClick = { actions.onOpenOrder(orderId) },
                icon = PmIcons.Orders,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when {
            detail.isImported -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PmSecondaryButton(
                        text = stringResource(if (detail.clientId == null) R.string.feature_clients_entry_choose else R.string.feature_clients_entry_move),
                        onClick = actions.onMove,
                        icon = PmIcons.Clients,
                        modifier = Modifier.weight(1f),
                    )
                    if (!isInvoice && detail.clientId != null) {
                        PmSecondaryButton(
                            text = stringResource(R.string.feature_clients_entry_detach),
                            onClick = actions.onDetach,
                            icon = PmIcons.Close,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    stringResource(if (isInvoice) R.string.feature_clients_entry_invoice_move_note else R.string.feature_clients_entry_learns),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
            detail.canDelete -> PmSecondaryButton(
                text = stringResource(R.string.feature_clients_delete),
                onClick = actions.onDelete,
                icon = PmIcons.Delete,
                modifier = Modifier.fillMaxWidth(),
            )
            detail.kind == EntryKind.REPAIR_CHARGE -> Text(
                stringResource(R.string.feature_clients_entry_repair_note),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
            )
        }

        if (detail.related.isNotEmpty()) {
            SectionTitle(stringResource(if (isInvoice) R.string.feature_clients_entry_paid_by else R.string.feature_clients_entry_pays))
            PmCard(Modifier.fillMaxWidth()) {
                detail.related.forEachIndexed { index, related ->
                    if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    RelatedRow(related) { actions.onOpenEntry(related.kind == EntryKind.INVOICE, related.id) }
                }
            }
        }

        if (detail.isImported) {
            SectionTitle(stringResource(R.string.feature_clients_entry_from_file))
            PmCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    detail.fileName?.let { file ->
                        Field(stringResource(R.string.feature_clients_entry_file), file + (detail.importedAt?.let { " · " + it.format(IMPORTED_AT) } ?: ""))
                    }
                    if (detail.fields.isEmpty()) {
                        Text(stringResource(R.string.feature_clients_entry_no_fields), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                    detail.fields.forEach { (title, value) -> Field(title, value) }
                }
            }
        }
    }
}

@Composable
private fun MatchTag(detail: ImportedEntryDetail) {
    val (text, tone) = when {
        detail.matchState == PaymentMatchState.PENDING -> stringResource(R.string.feature_clients_entry_state_pending) to TagTone.Warning
        detail.matchState == PaymentMatchState.IGNORED -> stringResource(R.string.feature_clients_entry_state_ignored) to TagTone.Neutral
        detail.matchReason == MatchReason.MANUAL || detail.matchState == PaymentMatchState.CONFIRMED ->
            stringResource(R.string.feature_clients_entry_by_you) to TagTone.Paid
        detail.matchReason == MatchReason.ACCOUNT -> stringResource(R.string.feature_clients_entry_by_account) to TagTone.Info
        detail.matchReason == MatchReason.ALIAS -> stringResource(R.string.feature_clients_entry_by_alias) to TagTone.Info
        detail.matchReason == MatchReason.INVOICE -> stringResource(R.string.feature_clients_entry_by_invoice) to TagTone.Info
        else -> stringResource(R.string.feature_clients_entry_by_name) to TagTone.Info
    }
    PmTag(text, tone)
}

@Composable
private fun RelatedRow(related: RelatedEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (related.kind == EntryKind.INVOICE) PmIcons.Invoice else PmIcons.Payments, contentDescription = null, tint = PmTheme.colors.primary)
        Column(Modifier.weight(1f)) {
            Text(related.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Text(related.date.formatShort(), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
        AmountText(related.amount, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Field(title: String, value: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 2.dp, top = 4.dp), style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun EntryDetailPreview() {
    PmTheme {
        EntryDetailScreen(
            EntryDetailUiState.Loaded(
                ImportedEntryDetail(
                    kind = EntryKind.BANK_PAYMENT, id = 1, date = LocalDate.of(2025, 1, 17), amount = Money.ofDram(4_000),
                    clientId = 3, clientName = "«ԻՆԹԵՐՆԵՅՇՆԼ ՄԵԴԻԱ ՀՈԼԴԻՆԳ» ՍՊԸ", documentNumber = "519302",
                    matchState = PaymentMatchState.AUTO, matchReason = MatchReason.INVOICE,
                    fileName = "bank.xlsx", importedAt = LocalDateTime.of(2026, 10, 9, 23, 50),
                    fields = listOf("Ամսաթիվ" to "17/01/2025", "Նպատակ" to "Հ/Վ  B5203593428 առ 08.01.2025", "Վճարող/Շահառու" to "\"ԻՆԹԵՐՆԵՅՇՆԼ ՄԵԴԻԱ ՀՈԼԴԻՆԳ\" ՍՊԸ"),
                    related = listOf(RelatedEntry(9, EntryKind.INVOICE, LocalDate.of(2025, 1, 8), Money.ofDram(4_000), "B5203593428")),
                ),
            ),
            EntryDetailActions(),
        )
    }
}
