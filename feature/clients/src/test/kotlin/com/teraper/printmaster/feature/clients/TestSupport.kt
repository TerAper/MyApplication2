package com.teraper.printmaster.feature.clients

import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteModelResult
import com.teraper.printmaster.core.data.repository.SaveModelResult
import com.teraper.printmaster.core.model.CatalogModel
import com.teraper.printmaster.core.model.ModelOwner
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.SaveMoneyEntryResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientAddress
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.MoneyEntryDraft
import java.time.LocalDate
import com.teraper.printmaster.core.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun summary(
    id: Long,
    name: String,
    type: ClientType = ClientType.FIRM,
    taxId: String? = null,
    phone: String? = null,
    balance: Long = 0,
) = ClientSummary(
    client = Client(id, name, type, taxId, "", listOfNotNull(phone?.let { ClientPhone(id, it, "") }), emptyList()),
    charged = Money.ofDram(balance.coerceAtLeast(0)),
    paid = Money.ofDram((-balance).coerceAtLeast(0)),
)

/** In-memory stand-in for the database-backed repository. */
class FakeClientsRepository(initial: List<ClientSummary> = emptyList()) : ClientsRepository {
    val clients = MutableStateFlow(initial)
    var blockedIds = emptySet<Long>()
    val savedDrafts = mutableListOf<ClientDraft>()

    override fun observeClientSummaries(): Flow<List<ClientSummary>> = clients

    override fun observeClientSummary(id: Long): Flow<ClientSummary?> =
        clients.map { list -> list.firstOrNull { it.client.id == id } }

    override suspend fun saveClient(draft: ClientDraft): SaveClientResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveClientResult.Invalid(errors)
        val clean = draft.normalized()
        clients.value.firstOrNull { it.client.taxId != null && it.client.taxId == clean.taxId && it.client.id != clean.id }
            ?.let { return SaveClientResult.TaxIdTaken(it.client.name) }

        savedDrafts += clean
        val id = if (clean.isNew) (clients.value.maxOfOrNull { it.client.id } ?: 0) + 1 else clean.id
        val client = Client(
            id, clean.name, clean.type, clean.taxId.ifEmpty { null }, clean.note,
            clean.phones.mapIndexed { i, p -> ClientPhone(i + 1L, p.value, p.label) },
            clean.addresses.mapIndexed { i, a -> ClientAddress(i + 1L, a.value, a.label, a.mapLink) },
        )
        clients.value = clients.value.filterNot { it.client.id == id } + ClientSummary(client)
        return SaveClientResult.Saved(id)
    }

    override suspend fun deleteClient(id: Long): DeleteClientResult = when {
        clients.value.none { it.client.id == id } -> DeleteClientResult.NOT_FOUND
        id in blockedIds -> DeleteClientResult.HAS_RECORDS
        else -> {
            clients.value = clients.value.filterNot { it.client.id == id }
            DeleteClientResult.DELETED
        }
    }
}

/** In-memory ledger per client. */
class FakePaymentsRepository : PaymentsRepository {
    val ledgers = MutableStateFlow<Map<Long, List<LedgerEntry>>>(emptyMap())
    val deleted = mutableListOf<LedgerEntry>()

    override fun observeLedger(clientId: Long): Flow<List<LedgerEntry>> = ledgers.map { it[clientId].orEmpty() }

    override fun observeIncome(from: LocalDate, to: LocalDate): Flow<IncomeTotals> = MutableStateFlow(IncomeTotals())

    override suspend fun saveMoneyEntry(draft: MoneyEntryDraft): SaveMoneyEntryResult = SaveMoneyEntryResult.Saved(1)

    override suspend fun deleteEntry(entry: LedgerEntry): Boolean {
        deleted += entry
        ledgers.value = ledgers.value.mapValues { (_, list) -> list - entry }
        return true
    }
}

/** Catalog holding whatever models the test puts in. */
class FakeCatalogRepository(models: List<PrinterModel> = emptyList()) : CatalogRepository {
    val catalog = MutableStateFlow(models.map { CatalogModel(it) })

    override fun observeCatalog(): Flow<List<CatalogModel>> = catalog

    override fun observeModel(id: Long): Flow<PrinterModel?> = catalog.map { list -> list.firstOrNull { it.model.id == id }?.model }

    override fun observeModelOwners(modelId: Long): Flow<List<ModelOwner>> = MutableStateFlow(emptyList())

    override suspend fun saveModel(draft: PrinterModelDraft): SaveModelResult = SaveModelResult.Saved(1)

    override suspend fun deleteModel(id: Long): DeleteModelResult = DeleteModelResult.DELETED
}
