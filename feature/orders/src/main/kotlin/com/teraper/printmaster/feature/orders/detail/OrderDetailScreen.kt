package com.teraper.printmaster.feature.orders.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.formatTime
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.component.relativeLabel
import com.teraper.printmaster.core.designsystem.component.tagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.nextActions
import com.teraper.printmaster.feature.orders.R
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
internal fun OrderDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenRepair: (orderId: Long, repairId: Long) -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                OrderDetailEvent.Deleted -> onBack()
            }
        }
    }
    OrderDetailScreen(
        state = state,
        actions = OrderDetailActions(
            onBack = onBack,
            onEdit = onEdit,
            onOpenClient = onOpenClient,
            onCall = { context.open(Intent.ACTION_DIAL, "tel:$it") },
            onOpenMap = { context.open(Intent.ACTION_VIEW, "geo:0,0?q=" + android.net.Uri.encode(it)) },
            onStatusChange = viewModel::onStatusChange,
            onOpenRepair = onOpenRepair,
            onFinish = viewModel::onFinish,
            onConfirmReopen = viewModel::onConfirmReopen,
            onConfirmCancel = viewModel::onConfirmCancel,
            onDelete = viewModel::onDeleteClick,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDialog = viewModel::onDismissDialog,
        ),
    )
}

private fun Context.open(action: String, uri: String) {
    try {
        startActivity(Intent(action, uri.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No phone or map app on this device: nothing to do.
    }
}

internal data class OrderDetailActions(
    val onBack: () -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onOpenClient: (Long) -> Unit = {},
    val onCall: (String) -> Unit = {},
    val onOpenMap: (String) -> Unit = {},
    val onStatusChange: (OrderStatus) -> Unit = {},
    /** repairId 0 = add new work. */
    val onOpenRepair: (orderId: Long, repairId: Long) -> Unit = { _, _ -> },
    val onFinish: (paidInCash: Boolean) -> Unit = {},
    val onConfirmReopen: () -> Unit = {},
    val onConfirmCancel: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

@Composable
internal fun OrderDetailScreen(state: OrderDetailUiState, actions: OrderDetailActions, modifier: Modifier = Modifier) {
    val loaded = state as? OrderDetailUiState.Loaded
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_orders_order),
            navigationLabel = stringResource(R.string.feature_orders_back),
            onNavigate = actions.onBack,
            actions = {
                if (loaded != null) {
                    IconButton(onClick = { actions.onEdit(loaded.order.id) }) {
                        Icon(PmIcons.Edit, contentDescription = stringResource(R.string.feature_orders_edit))
                    }
                    IconButton(onClick = actions.onDelete) {
                        Icon(PmIcons.Delete, contentDescription = stringResource(R.string.feature_orders_delete))
                    }
                }
            },
        )
        when (state) {
            OrderDetailUiState.Loading -> Unit
            OrderDetailUiState.NotFound -> PmEmptyState(PmIcons.Orders, stringResource(R.string.feature_orders_not_found), "")
            is OrderDetailUiState.Loaded -> OrderContent(state, actions)
        }
    }

    if (loaded != null) {
        when (loaded.dialog) {
            OrderDetailDialog.CONFIRM_DELETE -> PmConfirmDialog(
                title = stringResource(R.string.feature_orders_delete_title),
                message = loaded.order.description,
                confirmText = stringResource(R.string.feature_orders_delete),
                dismissText = stringResource(R.string.feature_orders_cancel),
                onConfirm = actions.onConfirmDelete,
                onDismiss = actions.onDismissDialog,
                destructive = true,
            )
            OrderDetailDialog.DELETE_BLOCKED -> PmMessageDialog(
                title = stringResource(R.string.feature_orders_delete_blocked_title),
                message = stringResource(R.string.feature_orders_delete_blocked_message),
                okText = stringResource(R.string.feature_orders_ok),
                onDismiss = actions.onDismissDialog,
            )
            OrderDetailDialog.CONFIRM_CANCEL -> PmConfirmDialog(
                title = stringResource(R.string.feature_orders_cancel_order_title),
                message = null,
                confirmText = stringResource(R.string.feature_orders_action_cancel),
                dismissText = stringResource(R.string.feature_orders_keep_order),
                onConfirm = actions.onConfirmCancel,
                onDismiss = actions.onDismissDialog,
                destructive = true,
            )
            OrderDetailDialog.CONFIRM_REOPEN -> PmConfirmDialog(
                title = stringResource(R.string.feature_orders_reopen_title),
                message = stringResource(R.string.feature_orders_reopen_message),
                confirmText = stringResource(R.string.feature_orders_action_reopen),
                dismissText = stringResource(R.string.feature_orders_cancel),
                onConfirm = actions.onConfirmReopen,
                onDismiss = actions.onDismissDialog,
            )
            null -> Unit
        }
    }
}

@Composable
private fun OrderContent(state: OrderDetailUiState.Loaded, actions: OrderDetailActions) {
    val order = state.order
    val today = state.today
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${order.date.relativeLabel(today)} · ${order.scheduledAt.toLocalTime().formatTime()}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PmTheme.colors.ink,
                )
                PmTag(order.status.label(), order.status.tagTone())
            }
            Text(order.description, style = MaterialTheme.typography.bodyLarge, color = PmTheme.colors.ink)

            PmCard(Modifier.fillMaxWidth()) {
                InfoRow(PmIcons.Clients, order.clientName, stringResource(R.string.feature_orders_open_client)) { actions.onOpenClient(order.clientId) }
                order.address?.let { address ->
                    HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    InfoRow(PmIcons.Map, address, stringResource(R.string.feature_orders_map)) { actions.onOpenMap(address) }
                }
                order.phone?.let { phone ->
                    HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    InfoRow(PmIcons.Call, phone, stringResource(R.string.feature_orders_call)) { actions.onCall(phone) }
                }
                order.masterName?.let { master ->
                    HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    InfoRow(PmIcons.Master, master, stringResource(R.string.feature_orders_master), onClick = null)
                }
            }

            if (state.canEditWork || state.work.repairs.isNotEmpty()) {
                WorkSection(
                    work = state.work,
                    canEdit = state.canEditWork,
                    onOpenRepair = { actions.onOpenRepair(order.id, it) },
                )
            }
        }

        // Status buttons: the main next step is the big one.
        val next = order.status.nextActions()
        Column(
            Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canFinishWithWork) {
                FinishButtons(state.work.total, actions)
                return@Column
            }
            next.firstOrNull()?.let { main ->
                PmPrimaryButton(
                    text = main.actionLabel(),
                    onClick = { actions.onStatusChange(main) },
                    containerColor = if (main == OrderStatus.DONE) PmTheme.colors.paid else PmTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (next.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    next.drop(1).forEach { status ->
                        PmSecondaryButton(text = status.actionLabel(), onClick = { actions.onStatusChange(status) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderStatus.actionLabel(): String = stringResource(
    when (this) {
        OrderStatus.NEW -> R.string.feature_orders_action_reopen
        OrderStatus.IN_PROGRESS -> R.string.feature_orders_action_start
        OrderStatus.DONE -> R.string.feature_orders_action_done
        OrderStatus.CANCELLED -> R.string.feature_orders_action_cancel
    },
)

@Composable
private fun InfoRow(icon: ImageVector, text: String, actionLabel: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = PmTheme.colors.primary)
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (onClick != null) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = PmTheme.colors.primary)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun OrderDetailPreview() {
    PmTheme {
        OrderDetailScreen(
            state = OrderDetailUiState.Loaded(
                Order(
                    1, 1, 1, "«ԱԲԳ Սերվիս» ՍՊԸ", LocalDateTime.of(2026, 10, 9, 10, 0), "Լիցքավորել 2 քարտրիջ, ստուգել գունավոր տպիչը",
                    address = "Կոմիտաս 5", phone = "091 123456", masterName = "Վարդան",
                ),
                today = LocalDate.of(2026, 10, 8),
            ),
            actions = OrderDetailActions(),
        )
    }
}
