package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.teraper.printmaster.core.database.entity.ClientAddressEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ClientPhoneEntity
import com.teraper.printmaster.core.database.model.ClientTotal
import com.teraper.printmaster.core.database.model.ClientWithContacts
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Transaction
    @Query("SELECT * FROM clients")
    fun observeClientsWithContacts(): Flow<List<ClientWithContacts>>

    @Transaction
    @Query("SELECT * FROM clients WHERE id = :id")
    fun observeClientWithContacts(id: Long): Flow<ClientWithContacts?>

    @Query("SELECT client_id, SUM(amount_minor) AS total FROM charges WHERE client_id IS NOT NULL GROUP BY client_id")
    fun observeChargeTotals(): Flow<List<ClientTotal>>

    @Query("SELECT client_id, SUM(amount_minor) AS total FROM payments WHERE client_id IS NOT NULL GROUP BY client_id")
    fun observePaymentTotals(): Flow<List<ClientTotal>>

    /** Day (epoch day) of each client's most recent payment. */
    @Query("SELECT client_id, MAX(date_epoch_day) AS total FROM payments WHERE client_id IS NOT NULL GROUP BY client_id")
    fun observeLastPaymentDays(): Flow<List<ClientTotal>>

    @Query("SELECT client_id, COUNT(*) AS total FROM client_printers GROUP BY client_id")
    fun observePrinterCounts(): Flow<List<ClientTotal>>

    @Query("SELECT * FROM clients WHERE tax_id = :taxId AND id != :excludeId LIMIT 1")
    suspend fun findOtherClientWithTaxId(taxId: String, excludeId: Long): ClientEntity?

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClient(id: Long): ClientEntity?

    /** Orders, charges and payments that stop the client from being deleted. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM orders WHERE client_id = :id)
             + (SELECT COUNT(*) FROM charges WHERE client_id = :id)
             + (SELECT COUNT(*) FROM payments WHERE client_id = :id)
        """,
    )
    suspend fun countRecordsBlockingDelete(id: Long): Int

    @Insert
    suspend fun insertClient(client: ClientEntity): Long

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClient(id: Long)

    // Phones and addresses are updated in place (not deleted and re-added) so that
    // orders pointing at a phone or address keep that link after the client is edited.

    @Upsert
    suspend fun upsertPhones(phones: List<ClientPhoneEntity>)

    @Query("DELETE FROM client_phones WHERE client_id = :clientId AND id NOT IN (:keepIds)")
    suspend fun deletePhonesExcept(clientId: Long, keepIds: List<Long>)

    @Upsert
    suspend fun upsertAddresses(addresses: List<ClientAddressEntity>)

    @Query("DELETE FROM client_addresses WHERE client_id = :clientId AND id NOT IN (:keepIds)")
    suspend fun deleteAddressesExcept(clientId: Long, keepIds: List<Long>)
}
