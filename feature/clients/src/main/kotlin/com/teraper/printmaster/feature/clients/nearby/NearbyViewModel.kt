package com.teraper.printmaster.feature.clients.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.nearby.LocateResult
import com.teraper.printmaster.core.data.nearby.NearbyRepository
import com.teraper.printmaster.core.model.GeoMath
import com.teraper.printmaster.core.model.MapPoint
import com.teraper.printmaster.core.model.NearbyClient
import com.teraper.printmaster.core.model.NearbyRadius
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NearbyUiState(
    /** Where the user is; null until found. */
    val here: MapPoint? = null,
    val findingHere: Boolean = true,
    /** Location is off or not allowed. */
    val noLocation: Boolean = false,
    val radius: NearbyRadius = NearbyRadius.KM2,
    /** Clients within [radius], nearest first. */
    val clients: List<NearbyClient> = emptyList(),
    /** Looking up typed addresses on the map: (done, total). */
    val locating: Pair<Int, Int>? = null,
    val offline: Boolean = false,
    /** Addresses the map couldn't place (typed too vaguely). */
    val notFound: Int = 0,
)

private data class Local(
    val here: MapPoint? = null,
    val findingHere: Boolean = true,
    val noLocation: Boolean = false,
    val radius: NearbyRadius = NearbyRadius.KM2,
    val locating: Pair<Int, Int>? = null,
    val offline: Boolean = false,
)

/** "Clients nearby": every client with an address within a distance of where the user stands. */
@HiltViewModel
class NearbyViewModel @Inject constructor(private val nearby: NearbyRepository) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<NearbyUiState> = combine(local, nearby.observeAddresses()) { local, addresses ->
        NearbyUiState(
            here = local.here,
            findingHere = local.findingHere,
            noLocation = local.noLocation,
            radius = local.radius,
            clients = local.here?.let { GeoMath.nearby(it, addresses, local.radius) }.orEmpty(),
            locating = local.locating,
            offline = local.offline,
            notFound = addresses.count { it.notFound },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUiState())

    /** Finding where the user is (again). */
    fun onFindingHere() = local.update { it.copy(findingHere = true, noLocation = false) }

    /** Where the user is; then typed addresses not on the map yet are looked up, nearest area first. */
    fun onHere(point: MapPoint?) {
        local.update { it.copy(here = point ?: it.here, findingHere = false, noLocation = point == null && it.here == null) }
        if (local.value.locating != null) return
        viewModelScope.launch {
            local.update { it.copy(locating = 0 to 0, offline = false) }
            val result = nearby.locate(point ?: local.value.here) { done, total -> local.update { it.copy(locating = done to total) } }
            local.update { it.copy(locating = null, offline = result == LocateResult.OFFLINE) }
        }
    }

    fun onRadius(radius: NearbyRadius) = local.update { it.copy(radius = radius) }
}
