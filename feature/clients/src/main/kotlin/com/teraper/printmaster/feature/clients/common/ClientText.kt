package com.teraper.printmaster.feature.clients.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.feature.clients.R

@Composable
internal fun ClientType.label(): String = stringResource(
    when (this) {
        ClientType.FIRM -> R.string.feature_clients_type_firm
        ClientType.PRIVATE -> R.string.feature_clients_type_private
    },
)

/** Second line under a client's name: "ՀՎՀՀ 01234567 · 3 տպիչ" or "Անհատ · 091 123456". */
@Composable
internal fun ClientSummary.subtitle(): String {
    val parts = buildList {
        val taxId = client.taxId
        when {
            taxId != null -> add(stringResource(R.string.feature_clients_tax_id_value, taxId))
            else -> add(client.type.label())
        }
        if (client.type == ClientType.PRIVATE || taxId == null) {
            client.phones.firstOrNull()?.let { add(it.number) }
        }
        if (printerCount > 0) {
            add(pluralStringResource(R.plurals.feature_clients_printer_count, printerCount, printerCount))
        }
    }
    return parts.joinToString(" · ")
}
