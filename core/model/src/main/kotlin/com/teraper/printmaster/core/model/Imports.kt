package com.teraper.printmaster.core.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Does the file belong to the company it is being imported into? */
enum class CompanyCheck {
    /** The file's ՀՎՀՀ or account is the company's. */
    MATCHES,

    /** The file names another ՀՎՀՀ or account: probably the wrong company or the wrong file. */
    DIFFERENT,

    /** The company has no ՀՎՀՀ / account saved yet, so it can't be checked; they can be saved from the file. */
    COMPANY_HAS_NONE,

    /** The file doesn't say whose it is. */
    FILE_HAS_NONE,
}

/** What importing a file would do, shown before anything is saved. */
data class ImportPreview(
    val kind: ImportKind,
    val fileName: String,
    val company: Company,
    val companyCheck: CompanyCheck,
    /** Another of the user's companies the file does belong to, when [companyCheck] is DIFFERENT. */
    val belongsTo: Company? = null,
    /** ՀՎՀՀ, account and owner name written in the file. */
    val fileTaxId: String? = null,
    val fileAccount: String? = null,
    val fileOwner: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val rows: Int = 0,
    val newRows: Int = 0,
    /** Rows already imported before (same fingerprint): skipped. */
    val alreadyImported: Int = 0,
    /** Invoices imported before whose amount, date or status changed: updated. */
    val changed: Int = 0,
    val newAmount: Money = Money.ZERO,
    /** Invoices: clients that don't exist yet and will be created. */
    val newClients: Int = 0,
    /** Bank: new payments attached automatically / waiting for the user. */
    val autoMatched: Int = 0,
    val toConfirm: Int = 0,
    /** Bank: rows that aren't client payments (outgoing, fees, interest). */
    val skipped: Int = 0,
) {
    val needsConfirmation: Boolean get() = companyCheck == CompanyCheck.DIFFERENT
}

sealed interface ImportPreviewResult {
    data class Ready(val preview: ImportPreview) : ImportPreviewResult
    data object NotExcel : ImportPreviewResult
    /** [missing]: when the file looks like one of the two, the needed columns it lacks. */
    data class UnknownLayout(val missing: MissingColumns? = null) : ImportPreviewResult
    data object NoCompany : ImportPreviewResult
}

data class ImportResult(
    val batchId: Long,
    val added: Int,
    val updated: Int,
    val createdClients: Int,
    /** Bank payments waiting for the user after this import (all files). */
    val pending: Int,
)

data class ImportBatch(
    val id: Long,
    val kind: ImportKind,
    val fileName: String,
    val importedAt: LocalDateTime,
    val rowCount: Int,
    val addedCount: Int,
    val skippedCount: Int,
)

/** A bank payment the app couldn't attach on its own. */
data class PendingPayment(
    val id: Long,
    val date: LocalDate,
    val amount: Money,
    val payerName: String,
    val purpose: String,
    val suggestedClientId: Long?,
    val suggestedClientName: String?,
)

/** Every kind of row in a client's money history. */
enum class EntryKind { INVOICE, BANK_PAYMENT, CASH_PAYMENT, MANUAL_CHARGE, REPAIR_CHARGE }

/** Everything known about one charge or payment, for its detail page. */
data class ImportedEntryDetail(
    val kind: EntryKind,
    val id: Long,
    val date: LocalDate,
    val amount: Money,
    val clientId: Long?,
    val clientName: String?,
    /** Invoice number, or the bank's document number. */
    val documentNumber: String?,
    val payerName: String? = null,
    val payerAccount: String? = null,
    val purpose: String? = null,
    val matchState: PaymentMatchState? = null,
    val matchReason: MatchReason? = null,
    val suggestedClientName: String? = null,
    val fileName: String?,
    val importedAt: LocalDateTime?,
    /** Every column of the original Excel row, in file order. Empty for rows imported before this was kept. */
    val fields: List<Pair<String, String>>,
    /** Payments naming this invoice, or invoices this payment names. */
    val related: List<RelatedEntry> = emptyList(),
    /** Full note: what was typed, the repair's work, or the bank purpose. */
    val note: String = "",
    /** When the row was entered or imported. */
    val createdAt: LocalDateTime? = null,
    /** The order a repair charge or a cash payment belongs to. */
    val orderId: Long? = null,
) {
    /** Came from an Excel file: can be moved to another client. */
    val isImported: Boolean get() = kind == EntryKind.INVOICE || kind == EntryKind.BANK_PAYMENT

    /** Typed on the phone: can be deleted. */
    val canDelete: Boolean get() = kind == EntryKind.CASH_PAYMENT || kind == EntryKind.MANUAL_CHARGE
}

data class RelatedEntry(val id: Long, val kind: EntryKind, val date: LocalDate, val amount: Money, val title: String)
