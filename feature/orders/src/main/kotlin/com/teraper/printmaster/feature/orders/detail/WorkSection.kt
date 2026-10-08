package com.teraper.printmaster.feature.orders.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.component.format
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.OrderWork
import com.teraper.printmaster.core.model.Repair
import com.teraper.printmaster.feature.orders.R

/** What was done on the visit: one card per device, then the totals. */
@Composable
internal fun WorkSection(work: OrderWork, canEdit: Boolean, onOpenRepair: (repairId: Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.feature_orders_work_title),
                modifier = Modifier.weight(1f).padding(start = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = PmTheme.colors.inkMuted,
            )
            if (canEdit && work.repairs.isNotEmpty()) {
                TextButton(onClick = { onOpenRepair(0) }) {
                    Icon(PmIcons.Add, contentDescription = null)
                    Text(stringResource(R.string.feature_orders_work_add_more))
                }
            }
        }

        if (work.repairs.isEmpty()) {
            PmCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.feature_orders_work_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.inkSecondary,
                    )
                    PmSecondaryButton(
                        text = stringResource(R.string.feature_orders_work_add),
                        onClick = { onOpenRepair(0) },
                        icon = PmIcons.Repair,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            return@Column
        }

        work.repairs.forEach { repair ->
            RepairCard(repair, onClick = if (canEdit) ({ onOpenRepair(repair.id) }) else null)
        }
        TotalsCard(work)
    }
}

@Composable
private fun RepairCard(repair: Repair, onClick: (() -> Unit)?) {
    PmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        repair.device?.name ?: stringResource(R.string.feature_orders_repair_no_device),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    repair.device?.printer?.location?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                }
                if (onClick != null) Icon(PmIcons.Edit, contentDescription = null, tint = PmTheme.colors.outlineStrong)
            }
            repair.lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (line.quantity > 1) "${line.name} ×${line.quantity}" else line.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AmountText(line.total, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (repair.note.isNotBlank()) {
                Text(repair.note, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = PmTheme.colors.inkMuted)
            }
        }
    }
}

@Composable
private fun TotalsCard(work: OrderWork) {
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TotalRow(stringResource(R.string.feature_orders_work_total), work.total, AmountTone.Neutral, MaterialTheme.typography.titleMedium)
            if (!work.cost.isZero) {
                TotalRow(stringResource(R.string.feature_orders_work_cost), work.cost, AmountTone.Neutral)
                TotalRow(
                    stringResource(R.string.feature_orders_work_profit),
                    work.profit,
                    if (work.profit.isNegative) AmountTone.Debt else AmountTone.Paid,
                )
            }
            if (work.isBilled) {
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                val unpaid = work.total - work.paidCash
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (work.paidCash.isPositive) {
                        PmTag(stringResource(R.string.feature_orders_work_paid_cash, work.paidCash.format()), TagTone.Paid)
                    }
                    if (unpaid.isPositive) {
                        PmTag(stringResource(R.string.feature_orders_work_on_account, unpaid.format()), TagTone.Debt)
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Money, tone: AmountTone, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = style)
        AmountText(amount, tone = tone, style = style)
    }
}

/** An open order with work: finish it by taking cash now, or leave it owed. */
@Composable
internal fun FinishButtons(total: Money, actions: OrderDetailActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PmPrimaryButton(
            text = stringResource(R.string.feature_orders_finish_cash, total.format()),
            onClick = { actions.onFinish(true) },
            containerColor = PmTheme.colors.paid,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PmSecondaryButton(
                text = stringResource(R.string.feature_orders_finish_on_account),
                onClick = { actions.onFinish(false) },
                modifier = Modifier.weight(1f),
            )
            PmSecondaryButton(
                text = stringResource(R.string.feature_orders_finish_cancel),
                onClick = { actions.onStatusChange(OrderStatus.CANCELLED) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
