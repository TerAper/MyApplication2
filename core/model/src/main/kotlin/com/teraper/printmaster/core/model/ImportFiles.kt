package com.teraper.printmaster.core.model

import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One invoice from the tax system's "issued invoices" export. */
data class ImportedInvoice(
    val serial: String,
    val clientTaxId: String?,
    val clientName: String,
    val issued: LocalDate,
    val amount: Money,
    val confirmedByBuyer: Boolean,
    val cancelled: Boolean,
    /** Every filled column of the row, as titled in the file, for the detail page. */
    val fields: List<Pair<String, String>> = emptyList(),
) {
    /** The invoice number is unique in the tax system, so it alone says "same invoice". */
    val fingerprint: String get() = "inv:" + serial.uppercase()
}

/** One incoming transfer from a client in a bank statement. */
data class ImportedPayment(
    val date: LocalDate,
    val documentNumber: String,
    val payerAccount: String?,
    val purpose: String,
    val amount: Money,
    val payerName: String,
    /** Every filled column of the row, as titled in the file, for the detail page. */
    val fields: List<Pair<String, String>> = emptyList(),
) {
    /**
     * Hidden key of the row: the bank reuses document numbers (fees share them), so date,
     * amount, payer account and purpose are part of it. Same row in two overlapping
     * statements → same fingerprint → imported once.
     */
    val fingerprint: String
        get() = "bank:" + ImportFiles.hash(
            documentNumber.trim(), date.toString(), amount.minor.toString(), payerAccount.orEmpty(), purpose.replace(Regex("""\s+"""), " ").trim(),
        )
}

sealed interface ImportFile {
    /** Invoices issued by the company with ՀՎՀՀ [issuerTaxId]. */
    data class Invoices(val issuerTaxId: String?, val issuerName: String?, val invoices: List<ImportedInvoice>) : ImportFile

    /** A statement of the company's account [account]; [skipped] = rows that aren't client payments. */
    data class BankStatement(
        val account: String?,
        val ownerName: String?,
        val ownerTaxId: String?,
        val payments: List<ImportedPayment>,
        val skipped: Int,
    ) : ImportFile
}

/** Recognises the two Excel files by their column titles and reads them. */
object ImportFiles {

    fun parse(rows: List<List<String?>>): ImportFile? = parseInvoices(rows) ?: parseBank(rows)

    // Invoice export of the tax system (Armenian titles).

    private fun parseInvoices(rows: List<List<String?>>): ImportFile.Invoices? {
        val headerIndex = rows.indexOfFirst { row -> row.any { it.cell() == "Սերիա և համար" } && row.any { it.cell() == "Ստացողի ՀՎՀՀ" } }
        if (headerIndex < 0) return null
        val header = rows[headerIndex].map { it.cell() }
        fun col(title: String) = header.indexOfFirst { it == title }
        fun colStarting(title: String) = header.indexOfFirst { it.startsWith(title) }
        val serial = col("Սերիա և համար")
        val taxId = col("Ստացողի ՀՎՀՀ")
        val personId = colStarting("Ստացողի անձը")
        val name = col("Ստացողի անվանում")
        val status = col("Կարգավիճակ")
        val issued = colStarting("Դուրս գրման ա")
        val amount = col("Արժեք")
        val issuerTaxId = col("Դուրս գրողի ՀՎՀՀ")
        val issuerName = col("Դուրս գրողի անվանում")
        if (serial < 0 || name < 0 || issued < 0 || amount < 0) return null

        val invoices = rows.drop(headerIndex + 1).mapNotNull { row ->
            val number = row.at(serial)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            val statusText = row.at(status).orEmpty()
            ImportedInvoice(
                serial = number,
                clientTaxId = (row.at(taxId) ?: row.at(personId))?.filter { it.isLetterOrDigit() }?.ifEmpty { null },
                clientName = row.at(name)?.trim().orEmpty(),
                issued = parseDate(row.at(issued)) ?: return@mapNotNull null,
                amount = parseAmount(row.at(amount)) ?: return@mapNotNull null,
                confirmedByBuyer = "Հաստատված" in statusText,
                cancelled = "Չեղարկ" in statusText || "Անվավեր" in statusText,
                fields = fields(rows[headerIndex], row),
            )
        }
        val first = rows.drop(headerIndex + 1).firstOrNull { it.at(serial) != null }
        return ImportFile.Invoices(
            issuerTaxId = first?.at(issuerTaxId)?.filter { it.isDigit() }?.ifEmpty { null },
            issuerName = first?.at(issuerName)?.trim(),
            invoices = invoices,
        )
    }

    // Bank account statement: a few "title | value" rows, then the table.

    private fun parseBank(rows: List<List<String?>>): ImportFile.BankStatement? {
        val headerIndex = rows.indexOfFirst { row -> row.any { it.cell() == "Ամսաթիվ" } && row.any { it.cell() == "Մուտք" } }
        if (headerIndex < 0) return null
        val header = rows[headerIndex].map { it.cell() }
        fun col(vararg titles: String) = header.indexOfFirst { it in titles }
        val date = col("Ամսաթիվ")
        val document = col("Փաստ.N", "Փաստ. N", "Փաստաթղթի N")
        val type = col("ԳՏ")
        val account = col("Հաշիվ")
        val purpose = col("Նպատակ")
        val incoming = col("Մուտք")
        val payer = col("Վճարող/Շահառու", "Վճարող")
        if (date < 0 || incoming < 0 || payer < 0) return null

        val top = rows.take(headerIndex)
        fun field(title: String) = top.firstOrNull { it.firstOrNull().cell() == title }
        var skipped = 0
        val payments = rows.drop(headerIndex + 1).mapNotNull { row ->
            val day = parseDate(row.at(date)) ?: return@mapNotNull null
            val amount = parseAmount(row.at(incoming))
            val payerName = row.at(payer)?.trim().orEmpty()
            // Outgoing transfers, bank fees and interest aren't client payments.
            if (amount == null || !amount.isPositive || payerName.isEmpty() || row.at(type)?.trim() in setOf("INT", "FEE")) {
                skipped++
                return@mapNotNull null
            }
            ImportedPayment(
                date = day,
                documentNumber = row.at(document)?.trim().orEmpty(),
                payerAccount = row.at(account)?.filter { it.isDigit() }?.ifEmpty { null },
                purpose = row.at(purpose)?.trim().orEmpty(),
                amount = amount,
                payerName = payerName,
                fields = fields(rows[headerIndex], row),
            )
        }
        val accountRow = field("Հաշիվ N")
        return ImportFile.BankStatement(
            account = accountRow?.at(1)?.filter { it.isDigit() }?.ifEmpty { null },
            ownerName = accountRow?.at(2)?.trim(),
            ownerTaxId = field("ՀՎՀՀ")?.at(1)?.filter { it.isDigit() }?.ifEmpty { null },
            payments = payments,
            skipped = skipped,
        )
    }

    private val DATE_TIME_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val DATE_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** Title → value of every filled cell; Excel day numbers in date columns become dates. */
    private fun fields(header: List<String?>, row: List<String?>): List<Pair<String, String>> =
        header.mapIndexedNotNull { index, rawTitle ->
            val title = rawTitle.cell().ifEmpty { return@mapIndexedNotNull null }
            val value = row.at(index)?.trim() ?: return@mapIndexedNotNull null
            val isDateColumn = "ա/թ" in title || "Ամսաթիվ" in title
            val shown: String = (if (isDateColumn) excelDateText(value) else null) ?: value
            title to shown
        }

    private fun excelDateText(value: String): String? {
        val serial = value.toDoubleOrNull()?.takeIf { it > 1 } ?: return null
        val dateTime = java.time.LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds((serial * 86_400).toLong())
        return if (dateTime.toLocalTime() == java.time.LocalTime.MIDNIGHT) dateTime.format(DATE_OUT) else dateTime.format(DATE_TIME_OUT)
    }

    private val DATE_FORMATS = listOf("dd/MM/yyyy", "dd.MM.yyyy", "yyyy-MM-dd", "dd-MM-yyyy").map(DateTimeFormatter::ofPattern)

    /** "17/01/2025", "08.01.2025", or Excel's day number ("45870.67" = 1 Aug 2025). */
    fun parseDate(text: String?): LocalDate? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toDoubleOrNull()?.let { serial -> return if (serial > 1) LocalDate.of(1899, 12, 30).plusDays(serial.toLong()) else null }
        val day = value.take(10)
        return DATE_FORMATS.firstNotNullOfOrNull { runCatching { LocalDate.parse(day, it) }.getOrNull() }
    }

    /** "4,000.00", "4000.0", "4 000,50" → Money. */
    fun parseAmount(text: String?): Money? {
        var value = text?.trim()?.replace(" ", "")?.replace(" ", "")?.takeIf { it.isNotEmpty() } ?: return null
        val comma = value.lastIndexOf(','); val dot = value.lastIndexOf('.')
        value = when {
            comma >= 0 && dot >= 0 -> if (dot > comma) value.replace(",", "") else value.replace(".", "").replace(',', '.')
            comma >= 0 && value.length - comma == 3 -> value.replace(',', '.')
            else -> value.replace(",", "")
        }
        val number = value.toBigDecimalOrNull() ?: return null
        return Money(number.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong())
    }

    /** Same ՀՎՀՀ, allowing the bank's longer form ("1386596363" contains "86596363"). */
    fun sameTaxId(a: String?, b: String?): Boolean {
        val x = a?.filter { it.isDigit() }.orEmpty(); val y = b?.filter { it.isDigit() }.orEmpty()
        if (x.length < 8 || y.length < 8) return false
        return x == y || x.endsWith(y) || y.endsWith(x)
    }

    fun sameAccount(a: String?, b: String?): Boolean {
        val x = a?.filter { it.isDigit() }.orEmpty(); val y = b?.filter { it.isDigit() }.orEmpty()
        return x.length >= 8 && x == y
    }

    internal fun hash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u001F").toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun String?.cell(): String = this?.trim()?.replace(Regex("""\s+"""), " ").orEmpty()

    private fun List<String?>.at(index: Int): String? = if (index < 0) null else getOrNull(index)?.takeIf { it.isNotBlank() }
}
