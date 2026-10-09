package com.teraper.printmaster.feature.clients.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.AmountTone
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTag
import com.teraper.printmaster.core.designsystem.component.TagTone
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.feature.clients.R

/** Finance tab: totals, add cash / debt, and the full money history. */
@Composable
internal fun FinanceSection(
    summary: ClientSummary,
    ledger: List<LedgerEntry>,
    onRecordPayment: () -> Unit,
    onAddCharge: () -> Unit,
    onEntryClick: (LedgerEntry) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalCard(stringResource(R.string.feature_clients_total_charged), summary.charged, AmountTone.Neutral, Modifier.weight(1f))
            TotalCard(stringResource(R.string.feature_clients_total_paid), summary.paid, AmountTone.Paid, Modifier.weight(1f))
            val balance = summary.balance
            TotalCard(
                label = stringResource(if (balance.isNegative) R.string.feature_clients_total_overpaid else R.string.feature_clients_total_debt),
                amount = if (balance.isNegative) -balance else balance,
                tone = when {
                    balance.isPositive -> AmountTone.Debt
                    balance.isNegative -> AmountTone.Overpaid
                    else -> AmountTone.Neutral
                },
                modifier = Modifier.weight(1f),
                container = if (balance.isPositive) PmTheme.colors.debtContainer else PmTheme.colors.surface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PmPrimaryButton(
                text = stringResource(R.string.feature_clients_record_cash),
                onClick = onRecordPayment,
                icon = PmIcons.Add,
                containerColor = PmTheme.colors.paid,
                modifier = Modifier.weight(1f),
            )
            PmSecondaryButton(
                text = stringResource(R.string.feature_clients_add_debt),
                onClick = onAddCharge,
                icon = PmIcons.Add,
                modifier = Modifier.weight(1f),
            )
        }

        PmCard(Modifier.fillMaxWidth()) {
            if (ledger.isEmpty()) {
                Text(stringResource(R.string.feature_clients_no_entries), modifier = Modifier.padding(14.dp), color = PmTheme.colors.inkMuted)
            }
            ledger.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                LedgerRow(entry, onClick = { onEntryClick(entry) })
            }
        }
        if (ledger.isNotEmpty()) {
            Text(stringResource(R.string.feature_clients_entry_open_hint), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
        }
    }
}

@Composable
private fun TotalCard(
    label: String,
    amount: Money,
    tone: AmountTone,
    modifier: Modifier = Modifier,
    container: Color = PmTheme.colors.surface,
) {
    PmCard(modifier, containerColor = container) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = PmTheme.colors.inkMuted, maxLines = 1)
            AmountText(amount, tone = tone, style = MaterialTheme.typography.titleSmall, withCurrency = false)
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val (tag, tone) = entry.tag()
        PmTag(tag, tone)
        Column(Modifier.weight(1f)) {
            Text(entry.details(), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (entry) {
            // Charges raise the debt (+), payments lower it (−).
            is LedgerEntry.Charge -> AmountText(entry.amount, tone = AmountTone.Neutral, style = MaterialTheme.typography.titleSmall, withSign = true, withCurrency = false)
            is LedgerEntry.Payment -> AmountText(-entry.amount, tone = AmountTone.Paid, style = MaterialTheme.typography.titleSmall, withCurrency = false)
        }
        Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
    }
}

@Composable
internal fun LedgerEntry.tag(): Pair<String, TagTone> = when (this) {
    is LedgerEntry.Charge -> when (source) {
        ChargeSource.INVOICE_IMPORT -> stringResource(R.string.feature_clients_tag_invoice) to TagTone.Debt
        ChargeSource.REPAIR -> stringResource(R.string.feature_clients_tag_repair) to TagTone.Debt
        ChargeSource.MANUAL -> stringResource(R.string.feature_clients_tag_debt) to TagTone.Debt
    }
    is LedgerEntry.Payment -> when (method) {
        PaymentMethod.CASH -> stringResource(R.string.feature_clients_tag_cash) to TagTone.Paid
        PaymentMethod.BANK -> stringResource(R.string.feature_clients_tag_bank) to TagTone.Info
    }
}

/** "07.10.2026 · No. 0451" — date plus invoice or transfer number when there is one. */
@Composable
private fun LedgerEntry.details(): String {
    val number = when (this) {
        is LedgerEntry.Charge -> documentNumber
        is LedgerEntry.Payment -> reference
    }
    return listOfNotNull(date.formatShort(), number?.let { stringResource(R.string.feature_clients_number, it) }).joinToString(" · ")
}
