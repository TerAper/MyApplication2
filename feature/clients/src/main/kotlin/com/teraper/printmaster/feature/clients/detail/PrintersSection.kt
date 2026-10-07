package com.teraper.printmaster.feature.clients.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.typeLabel
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.feature.clients.R

/** Client card → Printers tab: what the client owns and which cartridges each printer takes. */
@Composable
internal fun PrintersSection(
    printers: List<ClientPrinter>,
    onAddPrinter: () -> Unit,
    onPrinterClick: (Long) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (printers.isEmpty()) {
            Text(
                stringResource(R.string.feature_clients_no_printers),
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkMuted,
            )
        }
        printers.forEach { printer ->
            PrinterCard(printer, onClick = { onPrinterClick(printer.id) })
        }
        PmSecondaryButton(
            text = stringResource(R.string.feature_clients_add_printer),
            onClick = onAddPrinter,
            icon = PmIcons.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrinterCard(printer: ClientPrinter, onClick: () -> Unit) {
    PmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PmIcons.Printer, contentDescription = null, tint = PmTheme.colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(printer.model.fullName, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(printer.model.typeLabel(), printer.location).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
                if (printer.cartridges.isNotEmpty()) {
                    val cartridges = printer.cartridges.joinToString(", ") { owned ->
                        val chips = owned.cartridge.chips.joinToString(", ") { it.name }
                        if (chips.isEmpty()) owned.cartridge.name else "${owned.cartridge.name} ($chips)"
                    }
                    Text(
                        stringResource(R.string.feature_clients_printer_cartridges_value, cartridges),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.ink,
                    )
                }
                if (printer.note.isNotBlank()) {
                    Text(printer.note, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                }
            }
            Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}
