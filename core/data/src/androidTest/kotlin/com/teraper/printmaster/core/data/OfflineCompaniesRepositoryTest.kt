package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.DeleteCompanyResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyDraftError
import com.teraper.printmaster.core.model.MoneyEntryDraft
import com.teraper.printmaster.core.model.MoneyEntryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OfflineCompaniesRepositoryTest {

    private val repos = TestRepos()
    private val companies = repos.companies

    @After
    fun tearDown() = repos.db.close()

    @Test
    fun notRegisteredUntilRegistrationIsDone() = runTest {
        assertNull(companies.observeProfile().first())
        assertNull(companies.observeActiveCompany().first())
    }

    @Test
    fun masterRegistrationCreatesCompanyMasterAndDefault() = runTest {
        val id = (companies.register(
            AccountMode.MASTER, " Aram ",
            CompanyDraft(name = "«Ալֆա» ՍՊԸ", taxId = "0123 4567", bankAccounts = "2470-0000-1234-5678", colorIndex = 3),
        ) as SaveCompanyResult.Saved).companyId

        val profile = companies.observeProfile().first()!!
        assertEquals(AccountMode.MASTER, profile.mode)
        assertEquals(id, profile.defaultCompanyId)
        assertEquals(listOf("Aram"), companies.observeMasters().first().map { it.name })

        val company = companies.observeActiveCompany().first()!!
        assertEquals("01234567", company.taxId)
        assertEquals(listOf("2470000012345678"), company.bankAccounts)
        assertEquals(3, company.colorIndex)
    }

    @Test
    fun companyRegistrationHasNoMastersYet() = runTest {
        companies.register(AccountMode.COMPANY, "", CompanyDraft(name = "Service"))
        assertTrue(companies.observeMasters().first().isEmpty())
        assertTrue(companies.saveMaster(0, "Vardan", "091"))
        assertEquals(listOf("Vardan"), companies.observeMasters().first().map { it.name })
    }

    @Test
    fun invalidAndDuplicateCompaniesAreRejected() = runTest {
        companies.register(AccountMode.MASTER, "M", CompanyDraft(name = "A", taxId = "01234567"))
        assertEquals(
            SaveCompanyResult.Invalid(setOf(CompanyDraftError.NAME_REQUIRED, CompanyDraftError.BANK_ACCOUNT_FORMAT)),
            companies.saveCompany(CompanyDraft(bankAccounts = "12")),
        )
        assertEquals(SaveCompanyResult.TaxIdTaken("A"), companies.saveCompany(CompanyDraft(name = "B", taxId = "01234567")))
    }

    @Test
    fun deleteRules() = runTest {
        val main = repos.register()
        val empty = repos.addCompany("Empty")
        val used = repos.addCompany("Used")
        val client = (repos.clients.saveClient(ClientDraft(name = "C")) as SaveClientResult.Saved).clientId
        repos.payments.saveMoneyEntry(MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, client, used, "100", LocalDate.of(2026, 10, 7)))

        assertEquals(DeleteCompanyResult.IS_DEFAULT, companies.deleteCompany(main))
        assertEquals(DeleteCompanyResult.HAS_RECORDS, companies.deleteCompany(used))
        assertEquals(DeleteCompanyResult.DELETED, companies.deleteCompany(empty))

        companies.setDefaultCompany(used)
        assertEquals(used, companies.observeActiveCompany().first()!!.id)
        assertEquals(DeleteCompanyResult.DELETED, companies.deleteCompany(main))
    }

    @Test
    fun selectedCompanyFallsBackToDefaultWhenDeleted() = runTest {
        repos.register()
        val other = repos.addCompany("Other")
        companies.selectCompany(other)
        assertEquals("Other", companies.observeActiveCompany().first()!!.name)
        companies.deleteCompany(other)
        assertEquals("Main", companies.observeActiveCompany().first()!!.name)
    }
}
