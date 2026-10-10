package com.teraper.printmaster.feature.team

import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.TeamMember
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.FakeSyncController
import com.teraper.printmaster.core.testing.FakeTeamRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.team.join.JoinError
import com.teraper.printmaster.feature.team.join.JoinViewModel
import com.teraper.printmaster.feature.team.space.TeamError
import com.teraper.printmaster.feature.team.space.TeamViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val team = FakeTeamRepository()
    private val sync = FakeSyncController()

    @Test
    fun companySignsInCreatesSpaceAndLinksAMaster() = runTest {
        val vm = TeamViewModel(team, sync, FakeCompaniesRepository())
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onSignInResult(null)
        assertEquals(TeamError.SIGN_IN, vm.uiState.value.error)
        vm.onDismissError()
        vm.onSignInResult("token")
        assertEquals("me@gmail.com", vm.uiState.value.team.email)

        vm.onCreateSpace()
        assertEquals("K7PQ2MXA", vm.uiState.value.team.space?.joinCode)
        assertEquals(1, sync.syncs)

        team.addMember(TeamMember("u1", "Armen", "armen@gmail.com", null))
        vm.onLinkClick(vm.uiState.value.team.members.single())
        vm.onLinkTo(null)
        assertEquals(listOf("u1" to null), team.linked)
        assertNull(vm.uiState.value.linking)
        assertEquals(2, sync.syncs)
    }

    @Test
    fun masterJoinsWithTheCodeAndBecomesJoined() = runTest {
        val companies = FakeCompaniesRepository(registered = false)
        val vm = JoinViewModel(team, companies, sync)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        vm.onSignInResult("token")

        vm.onJoin()
        assertEquals(JoinError.NAME_REQUIRED, vm.uiState.value.error)
        vm.onNameChange("Armen")
        vm.onCodeChange("k7pq-2mx b")
        assertEquals("K7PQ2MXB", vm.uiState.value.code)
        vm.onJoin()
        assertEquals(JoinError.WRONG_CODE, vm.uiState.value.error)

        vm.onCodeChange("K7PQ2MXA")
        vm.onJoin()
        assertEquals(AccountMode.JOINED, companies.observeProfile().first()?.mode)
        assertEquals("Alfa", companies.observeCompanies().first().first().name)
        assertEquals(1, sync.syncs)
    }
}
