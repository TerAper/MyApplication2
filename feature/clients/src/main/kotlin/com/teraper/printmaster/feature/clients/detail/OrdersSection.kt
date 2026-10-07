package com.teraper.printmaster.feature.clients.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.component.PmOrderCard
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.feature.clients.R
import java.time.LocalDate

/** Client card → Orders tab: this client's visits with the company being viewed, newest first. */
@Composable
internal fun OrdersSection(
    orders: List<Order>,
    today: LocalDate?,
    onNewOrder: () -> Unit,
    onOrderClick: (Long) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PmSecondaryButton(
            text = stringResource(R.string.feature_clients_new_order),
            onClick = onNewOrder,
            icon = PmIcons.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        if (orders.isEmpty()) {
            Text(
                stringResource(R.string.feature_clients_no_orders),
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkMuted,
            )
        }
        orders.forEach { order ->
            PmOrderCard(order, onClick = { onOrderClick(order.id) }, today = today, showClient = false)
        }
    }
}
