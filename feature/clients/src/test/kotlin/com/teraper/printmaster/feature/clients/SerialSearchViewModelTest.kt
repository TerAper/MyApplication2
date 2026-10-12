package com.teraper.printmaster.feature.clients

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.core.testing.FakeOrdersRepository
import com.teraper.printmaster.core.testing.FakeClientItemsRepository
import com.teraper.printmaster.feature.clients.serial.SerialSearchViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SerialSearchViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC)
    private val printer = ClientItem(5, 1, CatalogItem(1, ItemType(1, "Printer"), "HP M125"), serialNumber = "CNB8K12345")

    @Test
    fun aScannedSerialShowsThePrintersClientAndItsOrders() = runTest {
        val printers = FakeClientItemsRepository().apply { serialMatches = listOf(SerialMatch(printer, "Gamma", exact = true)) }
        val orders = FakeOrdersRepository(listOf(Order(11, 1, 1, "Gamma", LocalDate.of(2026, 10, 1).atTime(10, 0), "Refill"))).apply {
            itemOrders[5] = listOf(11)
        }
        // The scanned barcode still has its label word.
        val vm = SerialSearchViewModel(SavedStateHandle(mapOf("serial" to "S/N: CNB8K12345")), printers, orders, clock)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        advanceTimeBy(300)

        assertEquals("CNB8K12345", vm.query.value)
        val hit = vm.uiState.value.hits.single()
        assertEquals("Gamma", hit.match.clientName)
        assertEquals(listOf("Refill"), hit.orders.map { it.description })

        printers.serialMatches = emptyList()
        vm.onQueryChange("ZZZZ")
        advanceTimeBy(300)
        assertTrue(vm.uiState.value.hits.isEmpty())
    }
}
