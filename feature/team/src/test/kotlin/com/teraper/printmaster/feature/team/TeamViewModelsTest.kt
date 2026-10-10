package com.teraper.printmaster.feature.team

import com.teraper.printmaster.core.model.AttachResult
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.FakeSyncController
import com.teraper.printmaster.core.testing.FakeTeamRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.team.signin.SignInViewModel
import com.teraper.printmaster.feature.team.space.TeamError
import com.teraper.printmaster.feature.team.space.TeamViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val companies = FakeCompaniesRepository(listOf(Company(1, "Delta"), Company(2, "Zeta")))
    private val team = FakeTeamRepository(companies = companies)
    private val sync = FakeSyncController()

    private suspend fun TestScope.signedInVm(): TeamViewModel {
        team.signIn("token")
        return TeamViewModel(team, sync, companies).also { vm -> backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} } }
    }

    @Test
    fun signInFailsThenWorks() = runTest {
        val vm = SignInViewModel(team)
        vm.onSignInStarted()
        assertTrue(vm.uiState.value.signingIn)
        vm.onToken(null)
        assertTrue(vm.uiState.value.failed)

        vm.onToken("token")
        assertFalse(vm.uiState.value.failed)
        assertEquals("me@gmail.com", team.state.value.email)
    }

    @Test
    fun eachOwnCompanyGetsItsOwnCodeAndListsItsAttachedMasters() = runTest {
        val vm = signedInVm()
        companies.masters.value = listOf(
            Master(7, "Armen", email = "armen@gmail.com", companyIds = setOf(1)),
            Master(8, "Vardan"),
        )
        assertNull(vm.uiState.value.own.first().company.joinCode)

        vm.onInvite(1)
        val delta = vm.uiState.value.own.single { it.company.id == 1L }
        assertEquals("INVITE22", delta.company.joinCode)
        assertEquals(listOf("Armen"), delta.masters.map { it.name })
        // Zeta has no code and no masters; Vardan (no app) isn't an attached master anywhere.
        assertTrue(vm.uiState.value.own.single { it.company.id == 2L }.masters.isEmpty())
        assertEquals(1, sync.syncs)

        vm.onRemoveClick(delta.company, delta.masters.single())
        vm.onConfirmRemove()
        assertEquals(listOf(1L to 7L), team.removed)

        team.internetWorks = false
        vm.onNewCode(1)
        assertEquals(TeamError.NEW_CODE, vm.uiState.value.error)
    }

    @Test
    fun enteringAnotherOwnersCodeAttachesTheirCompany() = runTest {
        companies.profile.value = companies.profile.value!!.copy(ownerName = "Armen")
        val vm = signedInVm()

        vm.onAttachClick()
        assertEquals("Armen", vm.uiState.value.attach!!.name)
        vm.onAttachCodeChange("k7pq-2mx")
        vm.onConfirmAttach()
        assertTrue(vm.uiState.value.attach!!.codeTooShort)

        vm.onAttachCodeChange("WRONGCDE")
        vm.onConfirmAttach()
        assertEquals(AttachResult.WrongCode, vm.uiState.value.attach!!.failure)

        vm.onAttachCodeChange("k7pq 2mxa")
        assertEquals("K7PQ2MXA", vm.uiState.value.attach!!.code)
        vm.onConfirmAttach()
        assertNull(vm.uiState.value.attach)
        assertEquals(AttachResult.Attached("Xerox", "Apo"), vm.uiState.value.justAttached)
        assertEquals(listOf("Xerox"), vm.uiState.value.attached.map { it.name })

        vm.onLeaveClick(vm.uiState.value.attached.single())
        vm.onConfirmLeave()
        assertFalse(vm.uiState.value.attached.single().isShared)
    }
}
