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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

internal class OfflineClientsRepository @Inject constructor(
    private val database: PrintMasterDatabase,
    private val clientDao: ClientDao,
    private val clock: Clock,
) : ClientsRepository {

    override fun observeClientSummaries(): Flow<List<ClientSummary>> = combine(
        clientDao.observeClientsWithContacts(),
        clientDao.observeChargeTotals(),
        clientDao.observePaymentTotals(),
        clientDao.observePrinterCounts(),
    ) { clients, charges, payments, printers ->
        val chargeBy = charges.byClient()
        val paymentBy = payments.byClient()
        val printersBy = printers.byClient()
        clients
            .map { row ->
                val id = row.client.id
                ClientSummary(
                    client = row.toModel(),
                    printerCount = (printersBy[id] ?: 0L).toInt(),
                    balance = Money((chargeBy[id] ?: 0L) - (paymentBy[id] ?: 0L)),
                )
            }
            .sortedBy { ClientSearch.normalizeText(it.client.name) }
    }

    override fun observeClientSummary(id: Long): Flow<ClientSummary?> =
        observeClientSummaries().map { list -> list.firstOrNull { it.client.id == id } }.distinctUntilChanged()

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
            clientDao.upsertAddresses(clean.addresses.map { ClientAddressEntity(it.id, clientId, it.value, it.label) })

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
    addresses = addresses.sortedBy { it.id }.map { ClientAddress(it.id, it.address, it.label) },
)
