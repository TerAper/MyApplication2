package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ImportKind
import com.teraper.printmaster.core.model.MatchReason
import com.teraper.printmaster.core.model.PaymentMatchState
import com.teraper.printmaster.core.model.PaymentMethod

/** One Excel file import, always for one company. Deleting it undoes the import (its rows cascade). */
@Entity(
    tableName = "import_batches",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["company_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["company_id"])],
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "company_id") val companyId: Long,
    val kind: ImportKind,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "row_count") val rowCount: Int,
    @ColumnInfo(name = "added_count") val addedCount: Int,
    @ColumnInfo(name = "skipped_count") val skippedCount: Int,
)

/**
 * Something a client owes: an imported invoice, a finished repair, or a manual entry.
 * [clientId] is null while an imported invoice has no matching client yet.
 */
@Entity(
    tableName = "charges",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["company_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RepairEntity::class,
            parentColumns = ["id"],
            childColumns = ["repair_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["import_batch_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["client_id"]),
        Index(value = ["repair_id"]),
        Index(value = ["import_batch_id"]),
        Index(value = ["date_epoch_day"]),
        // Re-importing the same invoice file adds nothing twice.
        Index(value = ["company_id"]),
        Index(value = ["company_id", "source", "document_number", "date_epoch_day", "amount_minor"], unique = true),
        // The same invoice in two overlapping files is imported once.
        Index(value = ["company_id", "fingerprint"], unique = true),
    ],
)
data class ChargeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "company_id") val companyId: Long,
    @ColumnInfo(name = "client_id") val clientId: Long?,
    val source: ChargeSource,
    @ColumnInfo(name = "document_number") val documentNumber: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    /** Name and ՀՎՀՀ exactly as written in the imported file. */
    @ColumnInfo(name = "raw_name") val rawName: String?,
    @ColumnInfo(name = "raw_tax_id") val rawTaxId: String?,
    @ColumnInfo(name = "repair_id") val repairId: Long?,
    @ColumnInfo(name = "import_batch_id") val importBatchId: Long?,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Hidden key of an imported row (see ImportedInvoice.fingerprint); null for charges typed on the phone. */
    val fingerprint: String? = null,
    /** All columns of the imported row as JSON [[title, value], …], shown on the detail page. */
    @ColumnInfo(name = "raw_data") val rawData: String? = null,
)

/**
 * Money received: cash entered on the phone, or a bank transfer from an imported statement.
 * [clientId] is null while an imported payment has no matching client yet.
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["company_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["suggested_client_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["import_batch_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["client_id"]),
        Index(value = ["order_id"]),
        Index(value = ["import_batch_id"]),
        Index(value = ["date_epoch_day"]),
        // Re-importing the same bank statement adds nothing twice.
        Index(value = ["company_id"]),
        // The bank reuses document numbers, so the hidden fingerprint is what makes a row unique.
        Index(value = ["company_id", "fingerprint"], unique = true),
        Index(value = ["suggested_client_id"]),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "company_id") val companyId: Long,
    @ColumnInfo(name = "client_id") val clientId: Long?,
    val method: PaymentMethod,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    /** Bank transfer number; null for cash. */
    val reference: String?,
    /** Payer name exactly as written in the bank statement. */
    @ColumnInfo(name = "raw_payer_name") val rawPayerName: String?,
    @ColumnInfo(name = "order_id") val orderId: Long?,
    @ColumnInfo(name = "import_batch_id") val importBatchId: Long?,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Hidden key of an imported bank row (see ImportedPayment.fingerprint); null for cash. */
    val fingerprint: String? = null,
    /** The payer's bank account, as in the statement. */
    @ColumnInfo(name = "payer_account") val payerAccount: String? = null,
    /** How the client was found, or that the user still has to say. Null for cash. */
    @ColumnInfo(name = "match_state") val matchState: PaymentMatchState? = null,
    /** Probable client while [matchState] is PENDING; [clientId] stays null until confirmed. */
    @ColumnInfo(name = "suggested_client_id") val suggestedClientId: Long? = null,
    /** How the client was found (account, remembered name, invoice number, name, by hand). */
    @ColumnInfo(name = "match_reason") val matchReason: MatchReason? = null,
    /** All columns of the imported row as JSON [[title, value], …], shown on the detail page. */
    @ColumnInfo(name = "raw_data") val rawData: String? = null,
)

/** "Payer [nameKey] is NOT client [clientId]": the user moved a payment away; never matched back. */
@Entity(
    tableName = "match_rejections",
    primaryKeys = ["name_key", "client_id"],
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["client_id"])],
)
data class MatchRejectionEntity(
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "client_id") val clientId: Long,
)

/**
 * A payer bank account seen on payments. [clientId] null = it paid for different clients,
 * so it isn't used to match any more.
 */
@Entity(
    tableName = "payer_accounts",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["client_id"])],
)
data class PayerAccountEntity(
    @PrimaryKey val account: String,
    @ColumnInfo(name = "client_id") val clientId: Long?,
)
