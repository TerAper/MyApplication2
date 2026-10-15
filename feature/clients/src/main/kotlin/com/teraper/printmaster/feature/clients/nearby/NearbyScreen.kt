package com.teraper.printmaster.feature.clients.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.MapWay
import com.teraper.printmaster.core.designsystem.component.PmEmptyState
import com.teraper.printmaster.core.designsystem.component.PmFilterChip
import com.teraper.printmaster.core.designsystem.component.PmMap
import com.teraper.printmaster.core.designsystem.component.PmPrimaryButton
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.component.rememberCurrentPoint
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.NearbyClient
import com.teraper.printmaster.core.model.NearbyRadius
import com.teraper.printmaster.feature.clients.R
import java.util.Locale

@Composable
internal fun NearbyRoute(onBack: () -> Unit, onOpenClient: (Long) -> Unit, viewModel: NearbyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val findHere = rememberCurrentPoint(viewModel::onHere)
    val refresh = {
        viewModel.onFindingHere()
        findHere()
    }
    // Where the user is, as soon as the screen opens.
    LaunchedEffect(Unit) { findHere() }
    NearbyScreen(state, onBack, onOpenClient, onRefresh = refresh, onRadius = viewModel::onRadius)
}

@Composable
internal fun NearbyScreen(
    state: NearbyUiState,
    onBack: () -> Unit,
    onOpenClient: (Long) -> Unit,
    onRefresh: () -> Unit,
    onRadius: (NearbyRadius) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PmTheme.colors.background)) {
        PmTopBar(
            title = stringResource(R.string.feature_clients_nearby_title),
            navigationLabel = stringResource(R.string.feature_clients_back),
            onNavigate = onBack,
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(PmIcons.MyLocation, contentDescription = stringResource(R.string.feature_clients_nearby_refresh), tint = PmTheme.colors.primary)
                }
            },
        )
        val here = state.here
        if (here == null) {
            if (state.findingHere) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PmTheme.colors.primary)
                    Text(stringResource(R.string.feature_clients_nearby_finding), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PmEmptyState(PmIcons.MyLocation, stringResource(R.string.feature_clients_nearby_title), stringResource(R.string.feature_clients_nearby_no_location))
                    PmPrimaryButton(stringResource(R.string.feature_clients_nearby_try_again), onRefresh, Modifier.fillMaxWidth(), icon = PmIcons.MyLocation)
                }
            }
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NearbyRadius.entries.forEach { radius ->
                PmFilterChip(text = distanceText(radius.meters.toDouble()), selected = radius == state.radius, onClick = { onRadius(radius) })
            }
        }
        PmMap(
            ways = listOf(MapWay(0, stringResource(R.string.feature_clients_nearby_you), "", listOf(here), colorIndex = 0, isMe = true)) +
                state.clients.map { MapWay(it.clientId, it.clientName, it.address, listOf(it.point), colorIndex = 1) },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
        Status(state)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (state.clients.isEmpty() && state.locating == null) {
                item {
                    Text(
                        stringResource(R.string.feature_clients_nearby_none, distanceText(state.radius.meters.toDouble())),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.inkMuted,
                    )
                }
            }
            items(state.clients, key = { it.clientId }) { client ->
                ClientRow(client, onClick = { onOpenClient(client.clientId) })
                HorizontalDivider(color = PmTheme.colors.surfaceMuted)
            }
        }
    }
}

/** How many there are; still looking up addresses; offline; addresses the map couldn't place. */
@Composable
private fun Status(state: NearbyUiState) {
    val lines = buildList {
        state.locating?.let { (done, total) -> if (total > 0) add(stringResource(R.string.feature_clients_nearby_locating, done, total)) }
        if (state.offline) add(stringResource(R.string.feature_clients_nearby_offline))
        if (state.notFound > 0 && state.locating == null) add(stringResource(R.string.feature_clients_nearby_not_found, state.notFound))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.feature_clients_nearby_count, state.clients.size, distanceText(state.radius.meters.toDouble())),
            style = MaterialTheme.typography.titleSmall,
        )
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted) }
    }
}

@Composable
private fun ClientRow(client: NearbyClient, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(PmTheme.colors.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(client.clientName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(client.address, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(distanceText(client.meters), style = MaterialTheme.typography.titleSmall, color = PmTheme.colors.primary)
    }
}

/** "850 մ", "2.4 կմ". */
@Composable
private fun distanceText(meters: Double): String = if (meters < 1_000) {
    stringResource(R.string.feature_clients_nearby_meters, meters.toInt())
} else {
    val km = meters / 1_000
    stringResource(R.string.feature_clients_nearby_km, if (km >= 10 || km % 1.0 == 0.0) String.format(Locale.ROOT, "%.0f", km) else String.format(Locale.ROOT, "%.1f", km))
}
