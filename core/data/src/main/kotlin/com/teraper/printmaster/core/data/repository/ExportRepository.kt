package com.teraper.printmaster.core.data.repository

/** Column titles etc. of the debt report, in the app's language. */
data class DebtReportLabels(
    val title: String,
    val client: String,
    val taxId: String,
    val phone: String,
    val charged: String,
    val paid: String,
    val balance: String,
    val lastPayment: String,
    val total: String,
)

/** Excel files made from the active company's data, to send to an accountant or a client. */
interface ExportRepository {

    /** "Debts-Alfa-Print-2026-10-09.xlsx" */
    suspend fun debtReportFileName(): String

    /** Writes the debt report to [uri] (a document the user picked). False if it could not be written. */
    suspend fun saveDebtReport(uri: String, onlyDebtors: Boolean, labels: DebtReportLabels): Boolean

    /** Writes the debt report into the app's cache for sharing; its path, or null on failure. */
    suspend fun cacheDebtReport(onlyDebtors: Boolean, labels: DebtReportLabels): String?
}
