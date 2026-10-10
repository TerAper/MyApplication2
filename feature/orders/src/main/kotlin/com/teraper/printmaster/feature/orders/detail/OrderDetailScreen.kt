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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import com.teraper.printmaster.core.designsystem.component.PhotoStripLabels
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmConfirmDialog
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmMessageDialog
import com.teraper.printmaster.core.designsystem.component.PmPhotoStrip
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmRecordingPlayerSheet
import com.teraper.printmaster.core.designsystem.component.PmRecordingRow
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.PmTextField
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.formatTime
import com.teraper.printmaster.core.designsystem.component.label
import com.teraper.printmaster.core.designsystem.component.relativeLabel
import com.teraper.printmaster.core.designsystem.component.tagTone
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.Photo
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
            onOpenMap = { target ->
                val isLink = target.startsWith("geo:") || target.startsWith("http")
                context.open(Intent.ACTION_VIEW, if (isLink) target else "geo:0,0?q=" + android.net.Uri.encode(target))
            },
            onStatusChange = viewModel::onStatusChange,
            onOpenRepair = onOpenRepair,
            onFinish = viewModel::onFinish,
            onFinishFor = viewModel::onFinishFor,
            onDismissFinishChoice = viewModel::onDismissFinishChoice,
            onDecline = viewModel::onDeclineClick,
            onDeclineReasonChange = viewModel::onDeclineReasonChange,
            onConfirmDecline = viewModel::onConfirmDecline,
            onConfirmReopen = viewModel::onConfirmReopen,
            onConfirmCancel = viewModel::onConfirmCancel,
            onDelete = viewModel::onDeleteClick,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDialog = viewModel::onDismissDialog,
            onPlayCall = viewModel::onPlayCall,
            onStopCall = viewModel::onStopCall,
            onAddPhoto = viewModel::onAddPhoto,
            onDeletePhoto = viewModel::onDeletePhoto,
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
    /** Attached company's order: null = done for it, else counted in that own company. */
    val onFinishFor: (companyId: Long?) -> Unit = {},
    val onDismissFinishChoice: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onDeclineReasonChange: (String) -> Unit = {},
    val onConfirmDecline: () -> Unit = {},
    val onConfirmReopen: () -> Unit = {},
    val onConfirmCancel: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onPlayCall: (CallRecording) -> Unit = {},
    val onStopCall: () -> Unit = {},
    val onAddPhoto: (String) -> Unit = {},
    val onDeletePhoto: (Photo) -> Unit = {},
)

@Composable
internal fun OrderDetailScreen(state: OrderDetailUiState, actions: OrderDetailActions, modifier: Modifier = Modifier) {
    OrderCallPlayer(state, actions)
    val loaded = state as? OrderDetailUiState.Loaded
    Column(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_orders_order),
            navigationLabel = stringResource(R.string.feature_orders_back),
            onNavigate = actions.onBack,
            actions = {
                // Another owner's order is planned by that owner: no editing or deleting here.
                if (loaded != null && !loaded.order.fromAttachedCompany) {
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
            OrderDetailDialog.DECLINE -> DeclineDialog(loaded, actions)
            null -> Unit
        }
        if (loaded.finishChoice != null) WhoseOrderDialog(loaded, actions)
    }
}

/** "Whose order is this?": done for the company that gave it, or counted in one of the user's companies. */
@Composable
private fun WhoseOrderDialog(state: OrderDetailUiState.Loaded, actions: OrderDetailActions) {
    val order = state.order
    AlertDialog(
        onDismissRequest = actions.onDismissFinishChoice,
        title = { Text(stringResource(R.string.feature_orders_whose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feature_orders_whose_message), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkSecondary)
                ChoiceCard(
                    title = stringResource(R.string.feature_orders_whose_theirs, order.companyName),
                    message = stringResource(R.string.feature_orders_whose_theirs_hint, order.companyName),
                    onClick = { actions.onFinishFor(null) },
                )
                state.ownCompanies.forEach { company ->
                    ChoiceCard(
                        title = stringResource(R.string.feature_orders_whose_mine, company.name),
                        message = stringResource(R.string.feature_orders_whose_mine_hint, order.companyName),
                        onClick = { actions.onFinishFor(company.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = actions.onDismissFinishChoice) { Text(stringResource(R.string.feature_orders_cancel)) } },
        containerColor = PmTheme.colors.surface,
    )
}

@Composable
private fun ChoiceCard(title: String, message: String, onClick: () -> Unit) {
    PmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun DeclineDialog(state: OrderDetailUiState.Loaded, actions: OrderDetailActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(stringResource(R.string.feature_orders_decline_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.feature_orders_decline_message, state.order.companyName),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkSecondary,
                )
                PmTextField(
                    value = state.declineReason,
                    onValueChange = actions.onDeclineReasonChange,
                    label = stringResource(R.string.feature_orders_decline_reason),
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmDecline) { Text(stringResource(R.string.feature_orders_decline), color = PmTheme.colors.error) }
        },
        dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.feature_orders_cancel)) } },
        containerColor = PmTheme.colors.surface,
    )
}

/** Orders between owners: where it came from, who turned it down, who took it. */
@Composable
private fun HandOverNote(order: Order) {
    val declinedBy = order.declinedBy
    val takenFrom = order.takenFrom
    val text = when {
        order.fromAttachedCompany -> stringResource(R.string.feature_orders_from_company, order.companyName, order.companyOwner.ifBlank { order.companyName })
        declinedBy != null && order.isOpen -> stringResource(R.string.feature_orders_declined_note, declinedBy, order.declinedReason.orEmpty())
        takenFrom != null -> stringResource(R.string.feature_orders_taken_from_note, takenFrom)
        order.takenByMaster -> stringResource(R.string.feature_orders_taken_note, order.masterName.orEmpty())
        else -> return
    }
    val warning = declinedBy != null && order.isOpen && !order.fromAttachedCompany
    PmCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (warning) PmIcons.Warning else PmIcons.Share, contentDescription = null, tint = if (warning) PmTheme.colors.warning else PmTheme.colors.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.inkSecondary)
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
            order.doneAt?.let { doneAt ->
                Text(
                    stringResource(R.string.feature_orders_done_at, doneAt.toLocalDate().relativeLabel(today), doneAt.toLocalTime().formatTime()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PmTheme.colors.paid,
                )
            }
            Text(order.description, style = MaterialTheme.typography.bodyLarge, color = PmTheme.colors.ink)
            HandOverNote(order)

            PmCard(Modifier.fillMaxWidth()) {
                InfoRow(PmIcons.Clients, order.clientName, stringResource(R.string.feature_orders_open_client)) { actions.onOpenClient(order.clientId) }
                order.address?.let { address ->
                    HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                    InfoRow(PmIcons.Map, address, stringResource(R.string.feature_orders_map)) { actions.onOpenMap(order.addressLink ?: address) }
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

            Text(
                stringResource(R.string.feature_orders_photos, state.photos.size),
                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = PmTheme.colors.inkMuted,
            )
            PmPhotoStrip(
                photos = state.photos,
                labels = PhotoStripLabels(
                    add = stringResource(R.string.feature_orders_photo_add),
                    camera = stringResource(R.string.feature_orders_photo_camera),
                    gallery = stringResource(R.string.feature_orders_photo_gallery),
                    delete = stringResource(R.string.feature_orders_photo_delete),
                    close = stringResource(R.string.feature_orders_photo_close),
                ),
                onAdd = actions.onAddPhoto,
                onDelete = actions.onDeletePhoto,
            )

            if (state.calls.isNotEmpty()) {
                Text(
                    stringResource(R.string.feature_orders_calls_that_day, state.calls.size),
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = PmTheme.colors.inkMuted,
                )
                PmCard(Modifier.fillMaxWidth()) {
                    state.calls.forEachIndexed { index, recording ->
                        if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                        PmRecordingRow(recording, onPlay = { actions.onPlayCall(recording) })
                    }
                }
            }
        }

        // Status buttons: the main next step is the big one.
        val next = order.status.nextActions()
        Column(
            Modifier.fillMaxWidth().background(PmTheme.colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canFinishWithWork) {
                FinishButtons(state.work.total, actions, attached = order.fromAttachedCompany)
                return@Column
            }
            // Another owner's order isn't cancelled here: it's turned down and goes back.
            val onStatus: (OrderStatus) -> Unit = { status ->
                if (order.fromAttachedCompany && status == OrderStatus.CANCELLED) actions.onDecline() else actions.onStatusChange(status)
            }
            next.firstOrNull()?.let { main ->
                PmPrimaryButton(
                    text = main.actionLabel(),
                    onClick = { onStatus(main) },
                    containerColor = if (main == OrderStatus.DONE) PmTheme.colors.paid else PmTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (next.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    next.drop(1).forEach { status ->
                        val label = if (order.fromAttachedCompany && status == OrderStatus.CANCELLED) stringResource(R.string.feature_orders_decline) else status.actionLabel()
                        PmSecondaryButton(text = label, onClick = { onStatus(status) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun OrderCallPlayer(state: OrderDetailUiState, actions: OrderDetailActions) {
    val recording = (state as? OrderDetailUiState.Loaded)?.playing ?: return
    PmRecordingPlayerSheet(
        recording = recording,
        missingText = stringResource(R.string.feature_orders_call_missing),
        playLabel = stringResource(R.string.feature_orders_call_play),
        pauseLabel = stringResource(R.string.feature_orders_call_pause),
        onDismiss = actions.onStopCall,
    )
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
