package com.teraper.printmaster.feature.orders.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.CompanyColors
import com.teraper.printmaster.core.designsystem.component.MapWay
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmMap
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.formatTime
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.feature.orders.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
internal fun MastersMapRoute(onBack: () -> Unit, onOrderClick: (Long) -> Unit, viewModel: MastersMapViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MastersMapScreen(state, onBack, onOrderClick)
}

/** All masters sharing where they are: their ways on one map, and a list to open each order. */
@Composable
internal fun MastersMapScreen(state: MastersMapUiState, onBack: () -> Unit, onOrderClick: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_orders_masters_map_title),
            navigationLabel = stringResource(R.string.feature_orders_back),
            onNavigate = onBack,
        )
        if (state.isLoading) return@Column
        if (state.tracks.isEmpty()) {
            PmEmptyState(PmIcons.MyLocation, stringResource(R.string.feature_orders_masters_map_title), stringResource(R.string.feature_orders_masters_map_empty))
            return@Column
        }
        PmMap(
            ways = state.tracks.mapIndexed { i, t -> MapWay(t.orderId, t.masterName, t.clientName, t.points, colorIndex = i) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).background(PmTheme.colors.surface)) {
            itemsIndexed(state.tracks, key = { _, t -> t.orderId }) { index, track ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOrderClick(track.orderId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(12.dp).background(CompanyColors.of(index), CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(track.masterName, style = MaterialTheme.typography.titleSmall)
                        Text(track.clientName, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                    }
                    Text(
                        track.last?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.at), ZoneId.systemDefault()).toLocalTime().formatTime() }
                            ?: stringResource(R.string.feature_orders_masters_map_no_point),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.inkSecondary,
                    )
                }
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            }
        }
    }
}
