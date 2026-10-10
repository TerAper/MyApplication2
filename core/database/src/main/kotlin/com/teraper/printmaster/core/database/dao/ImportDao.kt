package com.teraper.printmaster.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.database.entity.ClientAliasEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ImportBatchEntity
import com.teraper.printmaster.core.database.entity.MatchRejectionEntity
import com.teraper.printmaster.core.database.entity.PayerAccountEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.model.MatchReason
import com.teraper.printmaster.core.model.PaymentMatchState
import kotlinx.coroutines.flow.Flow

/** Excel imports: batches, imported rows and what matching remembers. */
@Dao
interface ImportDao {

    @Insert
    suspend fun insertBatch(batch: ImportBatchEntity): Long

    @Query("UPDATE import_batches SET added_count = :added, skipped_count = :skipped WHERE id = :id")
    suspend fun updateBatchCounts(id: Long, added: Int, skipped: Int)

    @Query("SELECT * FROM import_batches WHERE company_id = :companyId ORDER BY imported_at DESC")
    fun observeBatches(companyId: Long): Flow<List<ImportBatchEntity>>

    /** Undo: the batch's charges and payments go with it (foreign keys cascade). */
    @Query("DELETE FROM import_batches WHERE id = :id")
    suspend fun deleteBatch(id: Long): Int

    // Invoices

    @Query("SELECT id, fingerprint, amount_minor, date_epoch_day FROM charges WHERE company_id = :companyId AND fingerprint IS NOT NULL")
    suspend fun getChargeKeys(companyId: Long): List<ChargeKey>

    @Insert
    suspend fun insertCharge(charge: ChargeEntity): Long

    @Query("UPDATE charges SET amount_minor = :amountMinor, date_epoch_day = :epochDay WHERE id = :id")
    suspend fun updateCharge(id: Long, amountMinor: Long, epochDay: Long)

    @Query("DELETE FROM charges WHERE id = :id")
    suspend fun deleteCharge(id: Long)

    @Query("SELECT id, tax_id FROM clients WHERE tax_id IS NOT NULL AND attached_company_id IS NULL")
    suspend fun getClientTaxIds(): List<ClientTaxId>

    @Insert
    suspend fun insertClient(client: ClientEntity): Long

    // Bank

    @Query("SELECT id AS client_id, name AS number FROM clients WHERE attached_company_id IS NULL")
    suspend fun getClientNames(): List<ClientPhoneRow>

    @Query("SELECT * FROM client_aliases")
    suspend fun getAliases(): List<ClientAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: ClientAliasEntity)

    @Query("SELECT * FROM payer_accounts")
    suspend fun getPayerAccounts(): List<PayerAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayerAccount(account: PayerAccountEntity)

    @Query(
        """
        SELECT document_number, client_id, amount_minor FROM charges
        WHERE company_id = :companyId AND source = 'INVOICE_IMPORT' AND client_id IS NOT NULL AND document_number IS NOT NULL
        """,
    )
    suspend fun getInvoiceClients(companyId: Long): List<InvoiceClientRow>

    @Query("SELECT fingerprint FROM payments WHERE company_id = :companyId AND fingerprint IS NOT NULL")
    suspend fun getPaymentFingerprints(companyId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE company_id = :companyId AND match_state = 'PENDING'")
    suspend fun getPending(companyId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPayment(id: Long): PaymentEntity?

    @Query(
        """
        SELECT p.id, p.date_epoch_day, p.amount_minor, p.raw_payer_name, p.note, p.suggested_client_id, c.name AS suggested_name
        FROM payments p LEFT JOIN clients c ON c.id = p.suggested_client_id
        WHERE p.company_id = :companyId AND p.match_state = 'PENDING'
        ORDER BY p.date_epoch_day DESC, p.id DESC
        """,
    )
    fun observePending(companyId: Long): Flow<List<PendingRow>>

    @Query(
        "UPDATE payments SET client_id = :clientId, match_state = :state, suggested_client_id = :suggestedId, match_reason = :reason WHERE id = :id",
    )
    suspend fun setPaymentClient(id: Long, clientId: Long?, state: PaymentMatchState, suggestedId: Long?, reason: MatchReason? = null)

    // Corrections

    @Query("SELECT * FROM match_rejections")
    suspend fun getRejections(): List<MatchRejectionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRejection(rejection: MatchRejectionEntity)

    /** An alias pointing at a client the payer was moved away from. */
    @Query("DELETE FROM client_aliases WHERE normalized_name = :nameKey AND client_id = :clientId")
    suspend fun deleteAlias(nameKey: String, clientId: Long)

    @Query("UPDATE payer_accounts SET client_id = NULL WHERE account = :account AND client_id = :clientId")
    suspend fun forgetAccount(account: String, clientId: Long)

    @Query("SELECT * FROM charges WHERE id = :id")
    suspend fun getCharge(id: Long): ChargeEntity?

    @Query("UPDATE charges SET client_id = :clientId WHERE id = :id")
    suspend fun setChargeClient(id: Long, clientId: Long)

    // Detail pages

    @Query(
        """
        SELECT ch.*, c.name AS client_name, b.file_name, b.imported_at, r.order_id FROM charges ch
        LEFT JOIN clients c ON c.id = ch.client_id
        LEFT JOIN import_batches b ON b.id = ch.import_batch_id
        LEFT JOIN repairs r ON r.id = ch.repair_id
        WHERE ch.id = :id
        """,
    )
    fun observeChargeDetail(id: Long): Flow<ChargeDetailRow?>

    @Query(
        """
        SELECT p.*, c.name AS client_name, s.name AS suggested_name, b.file_name, b.imported_at FROM payments p
        LEFT JOIN clients c ON c.id = p.client_id
        LEFT JOIN clients s ON s.id = p.suggested_client_id
        LEFT JOIN import_batches b ON b.id = p.import_batch_id
        WHERE p.id = :id
        """,
    )
    fun observePaymentDetail(id: Long): Flow<PaymentDetailRow?>

    /** Imported invoices with these numbers (the ones a bank payment names). */
    @Query("SELECT * FROM charges WHERE company_id = :companyId AND source = 'INVOICE_IMPORT' AND document_number IN (:numbers)")
    suspend fun getInvoicesByNumber(companyId: Long, numbers: List<String>): List<ChargeEntity>

    /** Bank payments whose purpose names this invoice number. */
    @Query("SELECT * FROM payments WHERE company_id = :companyId AND method = 'BANK' AND note LIKE '%' || :number || '%'")
    suspend fun getPaymentsNaming(companyId: Long, number: String): List<PaymentEntity>
}

data class ChargeKey(
    val id: Long,
    val fingerprint: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "date_epoch_day") val epochDay: Long,
)

data class ClientTaxId(val id: Long, @ColumnInfo(name = "tax_id") val taxId: String)

data class InvoiceClientRow(
    @ColumnInfo(name = "document_number") val documentNumber: String,
    @ColumnInfo(name = "client_id") val clientId: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

data class PendingRow(
    val id: Long,
    @ColumnInfo(name = "date_epoch_day") val epochDay: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "raw_payer_name") val payerName: String?,
    val note: String,
    @ColumnInfo(name = "suggested_client_id") val suggestedClientId: Long?,
    @ColumnInfo(name = "suggested_name") val suggestedName: String?,
)

data class ChargeDetailRow(
    @Embedded val charge: ChargeEntity,
    @ColumnInfo(name = "client_name") val clientName: String?,
    @ColumnInfo(name = "file_name") val fileName: String?,
    @ColumnInfo(name = "imported_at") val importedAt: Long?,
    @ColumnInfo(name = "order_id") val orderId: Long?,
)

data class PaymentDetailRow(
    @Embedded val payment: PaymentEntity,
    @ColumnInfo(name = "client_name") val clientName: String?,
    @ColumnInfo(name = "suggested_name") val suggestedName: String?,
    @ColumnInfo(name = "file_name") val fileName: String?,
    @ColumnInfo(name = "imported_at") val importedAt: Long?,
)
