package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.ImportBatch
import com.teraper.printmaster.core.model.ImportedEntryDetail
import com.teraper.printmaster.core.model.ImportPreviewResult
import com.teraper.printmaster.core.model.ImportResult
import com.teraper.printmaster.core.model.PendingPayment
import kotlinx.coroutines.flow.Flow

/** Excel invoices and bank statements, into the active company. */
interface ImportRepository {

    /** Reads the file at [uri] and says what importing it would do. Nothing is saved. */
    suspend fun preview(uri: String, fileName: String): ImportPreviewResult

    /**
     * Imports the file last previewed. [saveCompanyIdentity]: the company had no ՀՎՀՀ/account,
     * save the file's on it so the next file is checked. Null if there is nothing to import.
     */
    suspend fun importPreviewed(saveCompanyIdentity: Boolean): ImportResult?

    fun observeBatches(): Flow<List<ImportBatch>>

    /** Removes everything the import added. */
    suspend fun undo(batchId: Long)

    /** Bank payments waiting for the user, newest first. */
    fun observePending(): Flow<List<PendingPayment>>

    /**
     * The payment is this client's (confirming a suggestion, picking one, or moving a payment
     * that went to the wrong client). The payer's name and account are remembered, so the next
     * import attaches them on its own; other waiting payments are matched again.
     */
    suspend fun assign(paymentId: Long, clientId: Long)

    /** Not a client payment (e.g. a private top-up): stays out of all balances. */
    suspend fun ignore(paymentId: Long)

    /** Any charge: imported invoice, repair or typed debt. */
    fun observeChargeDetail(chargeId: Long): Flow<ImportedEntryDetail?>

    /** Any payment: bank or cash. */
    fun observePaymentDetail(paymentId: Long): Flow<ImportedEntryDetail?>

    /** The invoice is another client's. Re-importing it later keeps this client. */
    suspend fun moveInvoice(chargeId: Long, clientId: Long)

    /**
     * The payment is NOT its current client's: it goes back to "to check", and this payer is
     * never matched to that client again (name and account).
     */
    suspend fun detachPayment(paymentId: Long)
}
