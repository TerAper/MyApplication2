package com.teraper.printmaster.feature.clients

import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.feature.clients.list.ClientFilter
import com.teraper.printmaster.feature.clients.list.ClientListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientListViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakeClientsRepository(
        listOf(
            summary(1, "«ԱԲԳ» ՍՊԸ", taxId = "01234567", balance = 180_000),
            summary(2, "Թիվ 5 դպրոց", taxId = "07654321"),
            summary(3, "Աննա Մկրտչյան", ClientType.PRIVATE, phone = "091 123456", balance = -5_000),
        ),
    )

    private fun names(vm: ClientListViewModel) = vm.uiState.value.clients.map { it.client.name }

    @Test
    fun filtersAndSearchCombine() = runTest {
        val vm = ClientListViewModel(repo)
        backgroundScope.launchCollect(vm)

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(3, vm.uiState.value.totalCount)

        vm.onFilterChange(ClientFilter.FIRMS)
        assertEquals(listOf("«ԱԲԳ» ՍՊԸ", "Թիվ 5 դպրոց"), names(vm))

        vm.onFilterChange(ClientFilter.IN_DEBT)
        assertEquals(listOf("«ԱԲԳ» ՍՊԸ"), names(vm))

        vm.onFilterChange(ClientFilter.ALL)
        vm.onQueryChange("091 12")
        assertEquals(listOf("Աննա Մկրտչյան"), names(vm))
        assertEquals(3, vm.uiState.value.totalCount)
    }

    @Test
    fun newClientsAppearLive() = runTest {
        val vm = ClientListViewModel(repo)
        backgroundScope.launchCollect(vm)

        repo.clients.value = repo.clients.value + summary(4, "Նոր")
        assertEquals(4, vm.uiState.value.totalCount)
    }

    private fun CoroutineScope.launchCollect(vm: ClientListViewModel) {
        launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
    }
}
