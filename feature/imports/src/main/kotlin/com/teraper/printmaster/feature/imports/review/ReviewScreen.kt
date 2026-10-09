package com.teraper.printmaster.feature.imports.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.AmountText
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmClientPickerSheet
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.formatShort
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PendingPayment
import com.teraper.printmaster.feature.imports.R
import java.time.LocalDate

@Composable
internal fun ReviewRoute(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewScreen(
        state = state,
        onBack = onBack,
        onConfirm = viewModel::onConfirm,
        onChooseClient = viewModel::onChooseClient,
        onIgnore = viewModel::onIgnore,
        onPickQueryChange = viewModel::onPickQueryChange,
        onClientPicked = viewModel::onClientPicked,
        onDismissPick = viewModel::onDismissPick,
    )
}

@Composable
internal fun ReviewScreen(
    state: ReviewUiState,
    onBack: () -> Unit,
    onConfirm: (PendingPayment) -> Unit = {},
    onChooseClient: (PendingPayment) -> Unit = {},
    onIgnore: (PendingPayment) -> Unit = {},
    onPickQueryChange: (String) -> Unit = {},
    onClientPicked: (Long) -> Unit = {},
    onDismissPick: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_imports_review_title),
            navigationLabel = stringResource(R.string.feature_imports_back),
            onNavigate = onBack,
        )
        if (state.isLoading) return@Column
        if (state.payments.isEmpty()) {
            PmEmptyState(
                icon = PmIcons.Check,
                title = stringResource(R.string.feature_imports_review_empty),
                message = stringResource(R.string.feature_imports_review_empty_message),
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    stringResource(R.string.feature_imports_review_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PmTheme.colors.inkSecondary,
                )
            }
            items(state.payments, key = { it.id }) { payment ->
                PendingCard(payment, onConfirm, onChooseClient, onIgnore)
            }
        }
    }
    state.pick?.let { pick ->
        PmClientPickerSheet(
            title = stringResource(R.string.feature_imports_whose_payment, pick.payment.payerName),
            searchHint = stringResource(R.string.feature_imports_search_hint),
            clearLabel = stringResource(R.string.feature_imports_clear),
            query = pick.query,
            clients = state.pickClients,
            onQueryChange = onPickQueryChange,
            onPick = onClientPicked,
            onDismiss = onDismissPick,
        )
    }
}

@Composable
private fun PendingCard(
    payment: PendingPayment,
    onConfirm: (PendingPayment) -> Unit,
    onChooseClient: (PendingPayment) -> Unit,
    onIgnore: (PendingPayment) -> Unit,
) {
    PmCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(payment.payerName, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                AmountText(payment.amount, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                payment.date.formatShort() + if (payment.purpose.isNotBlank()) " · " + payment.purpose else "",
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            payment.suggestedClientName?.let { name ->
                Text(stringResource(R.string.feature_imports_suggested, name), style = MaterialTheme.typography.bodyMedium, color = PmTheme.colors.primary)
                PmPrimaryButton(stringResource(R.string.feature_imports_confirm), { onConfirm(payment) }, Modifier.fillMaxWidth(), icon = PmIcons.Check)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onChooseClient(payment) }) {
                    Text(stringResource(if (payment.suggestedClientName == null) R.string.feature_imports_choose_client else R.string.feature_imports_other_client))
                }
                TextButton(onClick = { onIgnore(payment) }) {
                    Text(stringResource(R.string.feature_imports_not_client), color = PmTheme.colors.inkMuted)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 760)
@Composable
private fun ReviewScreenPreview() {
    PmTheme {
        ReviewScreen(
            ReviewUiState(
                isLoading = false,
                payments = listOf(
                    PendingPayment(1, LocalDate.of(2025, 3, 5), Money.ofDram(8_000), "Թիվ 56 ֆինանսական ապահովման բաժանմո ՀՀ ՊՆ 30573 զորամաս", "B0000000003", 3, "«ՀԱՅԱՍՏԱՆԻ ՊԱՇՏՊԱՆՈՒԹՅԱՆ ՆԱԽԱՐԱՐՈՒԹՅՈՒՆ»"),
                    PendingPayment(2, LocalDate.of(2025, 3, 6), Money.ofDram(5_000), "Կարապետյան Հակոբ Ժորայի", "Հաշվի համալրում", null, null),
                ),
            ),
            onBack = {},
        )
    }
}
