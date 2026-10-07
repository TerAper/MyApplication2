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
import androidx.compose.material3.Text
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
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientAddress
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.feature.clients.R
import com.teraper.printmaster.feature.clients.common.dial
import com.teraper.printmaster.feature.clients.common.label
import com.teraper.printmaster.feature.clients.common.openMap

@Composable
internal fun ClientDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
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
            is ClientDetailUiState.Loaded -> ClientDetailContent(state.summary, onCall, onOpenMap)
        }
    }

    if (state is ClientDetailUiState.Loaded) {
        when (state.dialog) {
            ClientDetailDialog.CONFIRM_DELETE -> PmConfirmDialog(
                title = stringResource(R.string.feature_clients_delete_title),
                message = stringResource(R.string.feature_clients_delete_message, state.summary.client.name),
                confirmText = stringResource(R.string.feature_clients_delete),
                dismissText = stringResource(R.string.feature_clients_cancel),
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDialog,
                destructive = true,
            )
            ClientDetailDialog.DELETE_BLOCKED -> PmMessageDialog(
                title = stringResource(R.string.feature_clients_delete_blocked_title),
                message = stringResource(R.string.feature_clients_delete_blocked_message),
                okText = stringResource(R.string.feature_clients_ok),
                onDismiss = onDismissDialog,
            )
            null -> Unit
        }
    }
}

@Composable
private fun ClientDetailContent(
    summary: ClientSummary,
    onCall: (String) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    val client = summary.client
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
            }
            BalanceBanner(summary.balance)
        }

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
            SectionTitle(stringResource(R.string.feature_clients_section_addresses))
            ContactCard(
                rows = client.addresses.map { it.address to it.label },
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
                    .clickable { onClick(value) }
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
                    balance = Money.ofDram(180_000),
                ),
            ),
            onBack = {}, onEdit = {}, onDelete = {}, onConfirmDelete = {}, onDismissDialog = {},
            onCall = {}, onOpenMap = {},
        )
    }
}
