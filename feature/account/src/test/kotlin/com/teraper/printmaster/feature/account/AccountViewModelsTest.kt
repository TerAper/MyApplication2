package com.teraper.printmaster.feature.account

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyDraftError
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.FakeTeamRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.account.companies.CompanyEditDialog
import com.teraper.printmaster.feature.account.companies.CompanyEditEvent
import com.teraper.printmaster.feature.account.companies.CompanyEditViewModel
import com.teraper.printmaster.feature.account.masters.MastersViewModel
import com.teraper.printmaster.feature.account.onboarding.OnboardingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    @Test
    fun registrationSuggestsTheGoogleNameAndNeedsNameAndCompany() = runTest {
        val repo = FakeCompaniesRepository(registered = false)
        val team = FakeTeamRepository().apply { signIn("token") }
        val vm = OnboardingViewModel(repo, team)
        assertEquals("Armen Petrosyan", vm.uiState.value.ownerName)
        assertEquals("me@gmail.com", vm.uiState.value.email)

        vm.onOwnerNameChange(" ")
        vm.onRegister()
        assertTrue(vm.uiState.value.ownerNameMissing)
        assertEquals(setOf(CompanyDraftError.NAME_REQUIRED), vm.uiState.value.errors)
        assertTrue(repo.registrations.isEmpty())

        vm.onOwnerNameChange("Armen")
        vm.onCompanyChange(CompanyDraft(name = "Delta", colorIndex = 2))
        assertFalse(vm.uiState.value.ownerNameMissing)
        assertTrue(vm.uiState.value.errors.isEmpty())
        vm.onRegister()

        assertEquals(AccountMode.OWNER, repo.registrations.single().first)
        assertEquals("Armen", repo.profile.value!!.ownerName)
    }

    @Test
    fun newCompanyGetsAnUnusedColorAndSaves() = runTest {
        val repo = FakeCompaniesRepository(listOf(Company(1, "Main", colorIndex = 0), Company(2, "B", colorIndex = 1)))
        val vm = CompanyEditViewModel(SavedStateHandle(), repo)
        assertEquals(2, vm.uiState.value.draft.colorIndex)

        vm.onChange(vm.uiState.value.draft.copy(name = "Third"))
        vm.onSave()
        assertEquals(CompanyEditEvent.Close, vm.events.first())
        assertEquals("Third", repo.savedCompanies.single().name)
    }

    @Test
    fun defaultCompanyCannotBeDeletedButOtherCanAfterMakingItDefault() = runTest {
        val repo = FakeCompaniesRepository(listOf(Company(1, "Main"), Company(2, "B")))
        val main = CompanyEditViewModel(SavedStateHandle(mapOf("companyId" to 1L)), repo)
        assertTrue(main.uiState.value.isDefault)
        main.onDeleteClick()
        assertEquals(CompanyEditDialog.DELETE_DEFAULT, main.uiState.value.dialog)

        val other = CompanyEditViewModel(SavedStateHandle(mapOf("companyId" to 2L)), repo)
        other.onMakeDefault()
        assertEquals(2L, repo.profile.value!!.defaultCompanyId)

        main.onDismissDialog()
        val mainAgain = CompanyEditViewModel(SavedStateHandle(mapOf("companyId" to 1L)), repo)
        mainAgain.onDeleteClick()
        assertEquals(CompanyEditDialog.CONFIRM_DELETE, mainAgain.uiState.value.dialog)
        mainAgain.onConfirmDelete()
        assertEquals(listOf("B"), repo.companies.value.map { it.name })
    }

    @Test
    fun mastersAreAddedEditedAndDeletedFromTheDialog() = runTest {
        val repo = FakeCompaniesRepository(mode = AccountMode.COMPANY)
        val vm = MastersViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onAdd()
        vm.onSave()
        assertTrue(vm.uiState.value.form!!.nameMissing)

        vm.onFormChange(vm.uiState.value.form!!.copy(name = "Vardan", phone = "091"))
        vm.onSave()
        assertNull(vm.uiState.value.form)
        assertEquals(listOf("Vardan"), vm.uiState.value.masters.map { it.name })

        vm.onEdit(vm.uiState.value.masters.single())
        vm.onDelete()
        assertTrue(vm.uiState.value.masters.isEmpty())
    }
}
