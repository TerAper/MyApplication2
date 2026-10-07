package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ImportKind
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
        Index(value = ["company_id", "method", "reference", "date_epoch_day", "amount_minor"], unique = true),
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
)
