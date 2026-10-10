package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.entity.ClientAddressEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ClientPhoneEntity
import com.teraper.printmaster.core.database.model.ClientTotal
import com.teraper.printmaster.core.database.model.ClientWithContacts
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientAddress
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class OfflineClientsRepository @Inject constructor(
    private val database: PrintMasterDatabase,
    private val clientDao: ClientDao,
    private val clock: Clock,
    private val companies: CompaniesRepository,
) : ClientsRepository {

    /** Clients are shared; their money is counted for the company being viewed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeClientSummaries(): Flow<List<ClientSummary>> =
        companies.observeActiveCompany().map { it?.id }.distinctUntilChanged().flatMapLatest { companyId ->
            if (companyId == null) {
                summaries(flowOf(emptyList()), flowOf(emptyList()), flowOf(emptyList()))
            } else {
                summaries(
                    clientDao.observeChargeTotals(companyId),
                    clientDao.observePaymentTotals(companyId),
                    clientDao.observeLastPaymentDays(companyId),
                )
            }
        }

    private fun summaries(
        chargeTotals: Flow<List<ClientTotal>>,
        paymentTotals: Flow<List<ClientTotal>>,
        lastPaymentDays: Flow<List<ClientTotal>>,
    ): Flow<List<ClientSummary>> = combine(
        clientDao.observeClientsWithContacts(),
        chargeTotals,
        paymentTotals,
        lastPaymentDays,
        clientDao.observePrinterCounts(),
    ) { clients, charges, payments, lastPayments, printers ->
        val chargeBy = charges.byClient()
        val paymentBy = payments.byClient()
        val lastPaymentBy = lastPayments.byClient()
        val printersBy = printers.byClient()
        clients
            .map { row ->
                val id = row.client.id
                ClientSummary(
                    client = row.toModel(),
                    printerCount = (printersBy[id] ?: 0L).toInt(),
                    charged = Money(chargeBy[id] ?: 0L),
                    paid = Money(paymentBy[id] ?: 0L),
                    lastPaymentDate = lastPaymentBy[id]?.let(LocalDate::ofEpochDay),
                )
            }
            .sortedBy { ClientSearch.normalizeText(it.client.name) }
    }

    /** An own client with its money, or an attached company's client (opened from its order) without. */
    override fun observeClientSummary(id: Long): Flow<ClientSummary?> =
        combine(observeClientSummaries(), clientDao.observeClientWithContacts(id)) { list, row ->
            list.firstOrNull { it.client.id == id } ?: row?.takeIf { it.client.attachedCompanyId != null }?.let { ClientSummary(it.toModel()) }
        }.distinctUntilChanged()

    override suspend fun saveClient(draft: ClientDraft): SaveClientResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveClientResult.Invalid(errors)
        val clean = draft.normalized()
        val taxId = clean.taxId.ifEmpty { null }

        return database.withTransaction {
            if (taxId != null) {
                clientDao.findOtherClientWithTaxId(taxId, excludeId = clean.id)?.let {
                    return@withTransaction SaveClientResult.TaxIdTaken(it.name)
                }
            }

            val clientId = if (clean.isNew) {
                clientDao.insertClient(
                    ClientEntity(
                        name = clean.name,
                        type = clean.type,
                        taxId = taxId,
                        note = clean.note,
                        createdAt = clock.millis(),
                    ),
                )
            } else {
                val existing = clientDao.getClient(clean.id) ?: return@withTransaction SaveClientResult.Invalid(emptySet())
                clientDao.updateClient(existing.copy(name = clean.name, type = clean.type, taxId = taxId, note = clean.note))
                clean.id
            }

            // Remove rows the user deleted, then update kept rows and insert new ones.
            clientDao.deletePhonesExcept(clientId, clean.phones.map { it.id }.filter { it != 0L })
            clientDao.upsertPhones(clean.phones.map { ClientPhoneEntity(it.id, clientId, it.value, it.label) })
            clientDao.deleteAddressesExcept(clientId, clean.addresses.map { it.id }.filter { it != 0L })
            clientDao.upsertAddresses(clean.addresses.map { ClientAddressEntity(it.id, clientId, it.value, it.label, it.mapLink) })

            SaveClientResult.Saved(clientId)
        }
    }

    override suspend fun deleteClient(id: Long): DeleteClientResult = database.withTransaction {
        when {
            clientDao.getClient(id) == null -> DeleteClientResult.NOT_FOUND
            clientDao.countRecordsBlockingDelete(id) > 0 -> DeleteClientResult.HAS_RECORDS
            else -> {
                clientDao.deleteClient(id)
                DeleteClientResult.DELETED
            }
        }
    }
}

private fun List<ClientTotal>.byClient(): Map<Long, Long> = associate { it.clientId to it.total }

private fun ClientWithContacts.toModel() = Client(
    id = client.id,
    name = client.name,
    type = client.type,
    taxId = client.taxId,
    note = client.note,
    phones = phones.sortedBy { it.id }.map { ClientPhone(it.id, it.number, it.label) },
    addresses = addresses.sortedBy { it.id }.map { ClientAddress(it.id, it.address, it.label, it.mapLink) },
    isAttached = client.attachedCompanyId != null,
)
