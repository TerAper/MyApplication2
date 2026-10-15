package com.teraper.printmaster.feature.clients

import com.teraper.printmaster.core.data.nearby.LocateResult
import com.teraper.printmaster.core.model.AddressPoint
import com.teraper.printmaster.core.model.MapPoint
import com.teraper.printmaster.core.model.NearbyRadius
import com.teraper.printmaster.core.testing.FakeNearbyRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.clients.nearby.NearbyViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val here = MapPoint(40.1777, 44.5126)
    private val repo = FakeNearbyRepository(
        listOf(
            AddressPoint(1, 10, "Near", "Abovyan 1", MapPoint(40.1790, 44.5140)),
            AddressPoint(2, 20, "Cascade", "Tamanyan", MapPoint(40.1911, 44.5153)),
            AddressPoint(3, 30, "Vague", "Komitas", null, notFound = true),
        ),
    )

    @Test
    fun clientsWithinTheRadiusNearestFirst() = runTest {
        val vm = NearbyViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.findingHere)

        vm.onHere(here)
        // Typed addresses are looked up around where the user is.
        assertEquals(listOf(here), repo.locatedNear)
        vm.onRadius(NearbyRadius.KM1)
        assertEquals(listOf("Near"), vm.uiState.value.clients.map { it.clientName })
        vm.onRadius(NearbyRadius.KM2)
        assertEquals(listOf("Near", "Cascade"), vm.uiState.value.clients.map { it.clientName })
        assertEquals(1, vm.uiState.value.notFound)
    }

    @Test
    fun noLocationAndOfflineAreSaid() = runTest {
        repo.result = LocateResult.OFFLINE
        val vm = NearbyViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        vm.onHere(null)
        assertTrue(vm.uiState.value.noLocation)
        assertTrue(vm.uiState.value.offline)
        assertTrue(vm.uiState.value.clients.isEmpty())
    }
}
