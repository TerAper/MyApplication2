package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One order: time on the left, client, what to do and where on the right.
 * [today] set = show the day too (lists that mix days); [showClient] false on the client's own card.
 */
@Composable
fun PmOrderCard(
    order: Order,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    showClient: Boolean = true,
) {
    val finished = !order.isOpen
    PmCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(56.dp)) {
                Text(
                    order.scheduledAt.toLocalTime().formatTime(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (finished) PmTheme.colors.inkMuted else PmTheme.colors.ink,
                )
                if (today != null) {
                    Text(order.date.relativeLabel(today), style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (showClient) {
                    Text(
                        order.clientName,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (finished) PmTheme.colors.inkMuted else PmTheme.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    order.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (order.status == OrderStatus.CANCELLED) TextDecoration.LineThrough else null,
                    ),
                    color = PmTheme.colors.inkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(order.address, order.masterName).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (order.status != OrderStatus.NEW) {
                    PmTag(order.status.label(), order.status.tagTone(), Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PmOrderCardPreview() {
    PmTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PmOrderCard(
                Order(1, 1, 1, "«ԱԲԳ Սերվիս» ՍՊԸ", LocalDateTime.of(2026, 10, 8, 10, 0), "Լիցքավորել 2 քարտրիջ", address = "Կոմիտաս 5"),
                onClick = {},
            )
            PmOrderCard(
                Order(2, 1, 1, "Թիվ 5 դպրոց", LocalDateTime.of(2026, 10, 6, 14, 0), "Թմբուկի փոխարինում", OrderStatus.IN_PROGRESS),
                onClick = {},
                today = LocalDate.of(2026, 10, 8),
            )
        }
    }
}
