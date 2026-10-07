package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ClientSummary

/** Full-height sheet to search and pick a client; shows each client's debt. Used by forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmClientPickerSheet(
    title: String,
    searchHint: String,
    clearLabel: String,
    query: String,
    clients: List<ClientSummary>,
    onQueryChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            PmSearchField(query = query, onQueryChange = onQueryChange, placeholder = searchHint, clearLabel = clearLabel)
        }
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(clients, key = { it.client.id }) { summary ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(summary.client.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        summary.client.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (summary.balance.isPositive) {
                        AmountText(summary.balance, tone = AmountTone.Debt, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            }
        }
    }
}
