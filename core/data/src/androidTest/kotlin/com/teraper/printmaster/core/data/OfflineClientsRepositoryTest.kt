package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraft.ContactDraft
import com.teraper.printmaster.core.model.ClientDraftError
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class OfflineClientsRepositoryTest {

    private lateinit var repos: TestRepos
    private lateinit var db: PrintMasterDatabase
    private lateinit var repo: OfflineClientsRepository
    private var companyId = 0L

    @Before
    fun setUp() = runTest {
        repos = TestRepos(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        db = repos.db
        repo = repos.clients
        companyId = repos.register()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(draft: ClientDraft): Long =
        (repo.saveClient(draft) as SaveClientResult.Saved).clientId

    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    @Test
    fun savesClientWithContacts() = runTest {
        val id = save(
            ClientDraft(
                name = "  ԱԲԳ ՍՊԸ ",
                taxId = "0123 4567",
                phones = listOf(ContactDraft(value = "091123456", label = "Office"), ContactDraft(value = " ")),
                addresses = listOf(ContactDraft(value = "Կոմիտաս 5")),
            ),
        )

        val client = repo.observeClientSummary(id).first()!!.client
        assertEquals("ԱԲԳ ՍՊԸ", client.name)
        assertEquals("01234567", client.taxId)
        assertEquals(listOf("091123456"), client.phones.map { it.number })
        assertEquals(listOf("Կոմիտաս 5"), client.addresses.map { it.address })
    }

    @Test
    fun editKeepsExistingPhoneIdsSoOrdersStayLinked() = runTest {
        val id = save(
            ClientDraft(
                name = "Firm",
                phones = listOf(ContactDraft(value = "111"), ContactDraft(value = "222")),
            ),
        )
        val before = repo.observeClientSummary(id).first()!!.client
        val kept = before.phones.first { it.number == "111" }

        val draft = ClientDraft.from(before).copy(
            phones = listOf(
                ContactDraft(kept.id, "111 changed", "Boss"),
                ContactDraft(value = "333"),
            ),
        )
        save(draft)

        val after = repo.observeClientSummary(id).first()!!.client
        assertEquals(listOf("111 changed", "333"), after.phones.map { it.number })
        assertEquals(kept.id, after.phones.first().id)
    }

    @Test
    fun taxIdMustBeUnique() = runTest {
        save(ClientDraft(name = "First", taxId = "01234567"))
        val result = repo.saveClient(ClientDraft(name = "Second", taxId = "01234567"))
        assertEquals(SaveClientResult.TaxIdTaken("First"), result)
    }

    @Test
    fun editingSameClientKeepsItsOwnTaxId() = runTest {
        val id = save(ClientDraft(name = "First", taxId = "01234567"))
        val result = repo.saveClient(ClientDraft(id = id, name = "First renamed", taxId = "01234567"))
        assertEquals(SaveClientResult.Saved(id), result)
    }

    @Test
    fun invalidDraftIsNotSaved() = runTest {
        val result = repo.saveClient(ClientDraft(name = "", taxId = "12"))
        assertEquals(
            SaveClientResult.Invalid(setOf(ClientDraftError.NAME_REQUIRED, ClientDraftError.TAX_ID_FORMAT)),
            result,
        )
        assertTrue(repo.observeClientSummaries().first().isEmpty())
    }

    @Test
    fun deleteRemovesClientAndItsContacts() = runTest {
        val id = save(ClientDraft(name = "Gone", phones = listOf(ContactDraft(value = "111"))))

        assertEquals(DeleteClientResult.DELETED, repo.deleteClient(id))
        assertNull(repo.observeClientSummary(id).first())
        val phonesLeft = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM client_phones").use {
            it.moveToFirst(); it.getInt(0)
        }
        assertEquals(0, phonesLeft)
    }

    @Test
    fun clientWithOrdersCannotBeDeleted() = runTest {
        val id = save(ClientDraft(name = "Busy"))
        sql("INSERT INTO orders (company_id, client_id, scheduled_at, description, status, created_at) VALUES ($companyId, $id, 0, 'x', 'NEW', 0)")

        assertEquals(DeleteClientResult.HAS_RECORDS, repo.deleteClient(id))
        assertEquals(DeleteClientResult.NOT_FOUND, repo.deleteClient(9999))
    }

    @Test
    fun balanceIsChargesMinusPayments() = runTest {
        val id = save(ClientDraft(name = "Debtor"))
        sql("INSERT INTO charges (company_id, client_id, source, amount_minor, date_epoch_day, note, created_at) VALUES ($companyId, $id, 'MANUAL', 4200000, 0, '', 0)")
        sql("INSERT INTO payments (company_id, client_id, method, amount_minor, date_epoch_day, note, created_at) VALUES ($companyId, $id, 'CASH', 1200000, 0, '', 0)")

        assertEquals(Money.ofDram(30_000), repo.observeClientSummary(id).first()!!.balance)
    }

    @Test
    fun clientsAreSortedByNameIgnoringCaseAndQuotes() = runTest {
        save(ClientDraft(name = "«Բ» ՍՊԸ"))
        save(ClientDraft(name = "ա անհատ", type = ClientType.PRIVATE))
        save(ClientDraft(name = "Գ"))

        assertEquals(
            listOf("ա անհատ", "«Բ» ՍՊԸ", "Գ"),
            repo.observeClientSummaries().first().map { it.client.name },
        )
    }
}
