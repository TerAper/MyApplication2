package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CompanyCheck
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.ImportPreview
import com.teraper.printmaster.core.model.ImportPreviewResult
import com.teraper.printmaster.core.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineImportRepositoryTest {

    private val repos = TestRepos()
    private val imports = repos.imports

    @After
    fun tearDown() = repos.db.close()

    private val invoiceHeader = listOf(
        "Հ/Հ", "Սերիա և համար", "Ստացողի ՀՎՀՀ", "Ստացողի անվանում", "Կարգավիճակ", "Դուրս գրման ա/թ", "Արժեք", "Դուրս գրողի ՀՎՀՀ",
    )

    private fun invoiceRow(serial: String, tax: String, name: String, amount: String, issuer: String = "86596363", status: String = "Հաստատված գնորդի կողմից") =
        listOf("1", serial, tax, name, status, "01.03.2025", amount, issuer)

    private val invoices = listOf(
        listOf("Դուրս գրված հաշիվ վավերագրեր"),
        invoiceHeader,
        invoiceRow("B0000000001", "00895184", "«ԱՅ ԹԻ ԳՐՈՒՊ» (ՍՊԸ)", "4000"),
        invoiceRow("B0000000002", "02884488", "«ԿՐԻՍՏԱԼ 7» (ՍՊԸ)", "6000"),
        invoiceRow("B0000000003", "08625223", "«ՋԻ ՓԻ» (ՓԲԸ)", "8000"),
    )

    private fun bank(vararg rows: List<String?>) = listOf(
        listOf("Հաշիվ N", "1150018363093024", "ՏԵՐ-ՍԱՐԳՍՅԱՆ ՍՈԽԱԿ ՄԵԽԱԿԻ ԱՁ"),
        listOf("ՀՎՀՀ", "1386596363"),
        listOf("Ամսաթիվ", "Փաստ.N", "ԳՏ", "Հաշիվ", "Նպատակ", "Մուտք", "Ելք", "Վճարող/Շահառու"),
    ) + rows.toList()

    private fun pay(doc: String, account: String, purpose: String, amount: String, payer: String) =
        listOf("05/03/2025", doc, "TRF", account, purpose, amount, "0.00", payer)

    private suspend fun preview(rows: List<List<String?>>, name: String = "file.xlsx"): ImportPreview =
        (imports.previewRows(rows, name) as ImportPreviewResult.Ready).preview

    private suspend fun companyWith(taxId: String = "", accounts: String = "") =
        (repos.companies.register(AccountMode.MASTER, "Master", CompanyDraft(name = "Alfa", taxId = taxId, bankAccounts = accounts)) as SaveCompanyResult.Saved).companyId

    @Test
    fun invoicesCreateClientsAndAreNeverAddedTwice() = runTest {
        val companyId = companyWith()
        val first = preview(invoices)
        assertEquals(CompanyCheck.COMPANY_HAS_NONE, first.companyCheck)
        assertEquals(3, first.newRows)
        assertEquals(3, first.newClients)
        assertEquals(Money.ofDram(18_000), first.newAmount)

        val result = imports.importPreviewed(saveCompanyIdentity = true)!!
        assertEquals(3, result.added)
        assertEquals(3, result.createdClients)
        assertEquals("86596363", repos.companies.observeCompanies().first().single { it.id == companyId }.taxId)
        // A bank file of the same ՀՎՀՀ adds the account to the company.
        preview(bank(pay("1", "1", "x", "10.00", "A")))
        imports.importPreviewed(false)
        assertEquals(listOf("1150018363093024"), repos.companies.observeCompanies().first().single { it.id == companyId }.bankAccounts)
        assertEquals(setOf("«ԱՅ ԹԻ ԳՐՈՒՊ» ՍՊԸ", "«ԿՐԻՍՏԱԼ 7» ՍՊԸ", "«ՋԻ ՓԻ» ՓԲԸ"), repos.clients.observeClientSummaries().first().map { it.client.name }.toSet())

        // Overlapping file: one old invoice with a new amount, one cancelled, one new.
        val again = preview(
            listOf(
                invoiceHeader,
                invoiceRow("B0000000001", "00895184", "«ԱՅ ԹԻ ԳՐՈՒՊ» (ՍՊԸ)", "4000"),
                invoiceRow("B0000000002", "02884488", "«ԿՐԻՍՏԱԼ 7» (ՍՊԸ)", "6500"),
                invoiceRow("B0000000003", "08625223", "«ՋԻ ՓԻ» (ՓԲԸ)", "8000", status = "Չեղարկված"),
                invoiceRow("B0000000004", "00895184", "«ԱՅ ԹԻ ԳՐՈՒՊ» (ՍՊԸ)", "3000"),
            ),
        )
        assertEquals(CompanyCheck.MATCHES, again.companyCheck)
        assertEquals(1, again.newRows)
        assertEquals(2, again.changed)
        assertEquals(1, again.alreadyImported)
        val second = imports.importPreviewed(false)!!
        assertEquals(1, second.added)
        assertEquals(2, second.updated)
        assertEquals(0, second.createdClients)
        assertEquals(3, repos.count("clients"))
        assertEquals(3, repos.count("charges"))
        val total = repos.clients.observeClientSummaries().first().sumOf { it.charged.minor }
        assertEquals(Money.ofDram(4_000 + 6_500 + 3_000).minor, total)
    }

    @Test
    fun fileOfAnotherCompanyIsFlagged() = runTest {
        companyWith(taxId = "11111111", accounts = "2200000000000001")
        val beta = repos.addCompany("Beta")
        repos.companies.saveCompany(CompanyDraft(id = beta, name = "Beta", taxId = "86596363"))
        val wrong = preview(invoices)
        assertEquals(CompanyCheck.DIFFERENT, wrong.companyCheck)
        assertEquals("Beta", wrong.belongsTo?.name)
        assertTrue(wrong.needsConfirmation)
        assertEquals(CompanyCheck.DIFFERENT, preview(bank(pay("1", "1", "x", "10.00", "A"))).companyCheck)
    }

    @Test
    fun bankPaymentsAreMatchedRememberedAndUndone() = runTest {
        companyWith(accounts = "1150018363093024")
        preview(invoices)
        imports.importPreviewed(false)
        val clients = repos.clients.observeClientSummaries().first().associate { it.client.name to it.client.id }

        val statement = bank(
            pay("10", "1570022633310100", "Հ/Վ B0000000001", "4,000.00", "\"ԱՅ ԹԻ ԳՐՈՒՊ\" ՍՊԸ"),
            pay("11", "11817081606001", "քարթրիջ", "6,000.00", "ԿՐԻՍՏԱԼ 7 ՍՊԸ մԱյԴի ԲանկՄ ՓԲԸ մԿենտրոնՄ մ/ճ"),
            pay("12", "900005000000", "B0000000003", "8,000.00", "Թիվ 56 ֆինանսական ապահովման բաժանմո ՀՀ ՊՆ 30573 զորամաս"),
            listOf("06/03/2025", "13", "FEE", null, "fee", "0.00", "100.00", null),
        )
        val p = preview(statement)
        assertEquals(CompanyCheck.MATCHES, p.companyCheck)
        assertEquals(3, p.newRows)
        assertEquals(2, p.autoMatched)
        assertEquals(1, p.toConfirm)
        assertEquals(1, p.skipped)
        val result = imports.importPreviewed(false)!!
        assertEquals(1, result.pending)

        val pending = imports.observePending().first().single()
        assertEquals(clients["«ՋԻ ՓԻ» ՓԲԸ"], pending.suggestedClientId)
        imports.assign(pending.id, pending.suggestedClientId!!)
        assertTrue(imports.observePending().first().isEmpty())

        // Same statement again: nothing new. A later payment from the same account matches by itself.
        assertEquals(0, preview(statement).newRows)
        imports.importPreviewed(false)
        val later = preview(bank(pay("20", "900005000000", "", "2,000.00", "ՊՆ 30573 զորամաս")))
        assertEquals(1, later.autoMatched)
        val laterBatch = imports.importPreviewed(false)!!.batchId

        suspend fun balance(name: String) = repos.clients.observeClientSummaries().first().single { it.client.name == name }.paid
        assertEquals(Money.ofDram(10_000), balance("«ՋԻ ՓԻ» ՓԲԸ"))
        imports.undo(laterBatch)
        assertEquals(Money.ofDram(8_000), balance("«ՋԻ ՓԻ» ՓԲԸ"))
        assertNull(imports.importPreviewed(false))
    }
}
