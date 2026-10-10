package com.teraper.printmaster.core.model

/**
 * A value the importer reads from an Excel file, found by the column's title.
 * [required] ones must be in the file, or it isn't recognised. [defaults] are the titles of
 * the tax-system and bank exports today; a title ending with `*` matches any title starting so.
 */
enum class ImportColumn(val kind: ImportKind, val required: Boolean, val defaults: List<String>, val isRowTitle: Boolean = false) {
    INVOICE_NUMBER(ImportKind.INVOICES, true, listOf("Սերիա և համար")),
    INVOICE_CLIENT_TAX_ID(ImportKind.INVOICES, false, listOf("Ստացողի ՀՎՀՀ")),
    INVOICE_CLIENT_PERSON_ID(ImportKind.INVOICES, false, listOf("Ստացողի անձը*")),
    INVOICE_CLIENT_NAME(ImportKind.INVOICES, true, listOf("Ստացողի անվանում")),
    INVOICE_STATUS(ImportKind.INVOICES, false, listOf("Կարգավիճակ")),
    INVOICE_DATE(ImportKind.INVOICES, true, listOf("Դուրս գրման ա*")),
    INVOICE_AMOUNT(ImportKind.INVOICES, true, listOf("Արժեք")),
    INVOICE_ISSUER_TAX_ID(ImportKind.INVOICES, false, listOf("Դուրս գրողի ՀՎՀՀ")),
    INVOICE_ISSUER_NAME(ImportKind.INVOICES, false, listOf("Դուրս գրողի անվանում")),

    BANK_DATE(ImportKind.BANK_STATEMENT, true, listOf("Ամսաթիվ")),
    BANK_DOCUMENT(ImportKind.BANK_STATEMENT, false, listOf("Փաստ.N", "Փաստ. N", "Փաստաթղթի N")),
    BANK_TYPE(ImportKind.BANK_STATEMENT, false, listOf("ԳՏ")),
    BANK_PAYER_ACCOUNT(ImportKind.BANK_STATEMENT, false, listOf("Հաշիվ")),
    BANK_PURPOSE(ImportKind.BANK_STATEMENT, false, listOf("Նպատակ")),
    BANK_INCOMING(ImportKind.BANK_STATEMENT, true, listOf("Մուտք")),
    BANK_PAYER(ImportKind.BANK_STATEMENT, true, listOf("Վճարող/Շահառու", "Վճարող")),

    /** Above the table: the row "Հաշիվ N | number | owner". */
    BANK_OWN_ACCOUNT(ImportKind.BANK_STATEMENT, false, listOf("Հաշիվ N"), isRowTitle = true),

    /** Above the table: the row "ՀՎՀՀ | number". */
    BANK_OWN_TAX_ID(ImportKind.BANK_STATEMENT, false, listOf("ՀՎՀՀ"), isRowTitle = true),
    ;

    companion object {
        fun of(kind: ImportKind) = entries.filter { it.kind == kind }
    }
}

/** Which titles the importer looks for; anything not set uses [ImportColumn.defaults]. */
data class ImportColumns(private val custom: Map<ImportColumn, List<String>> = emptyMap()) {

    fun titles(column: ImportColumn): List<String> = custom[column]?.takeIf { it.isNotEmpty() } ?: column.defaults

    fun isCustom(column: ImportColumn): Boolean = custom[column]?.isNotEmpty() == true && custom[column] != column.defaults

    /** Blank lines are dropped; no titles or the defaults again = back to default. */
    fun with(column: ImportColumn, titles: List<String>): ImportColumns {
        val clean = titles.map { normalize(it) }.filter { it.isNotEmpty() && it != "*" }.distinct()
        return ImportColumns(if (clean.isEmpty() || clean == column.defaults) custom - column else custom + (column to clean))
    }

    fun reset(kind: ImportKind) = ImportColumns(custom.filterKeys { it.kind != kind })

    val customized: Map<ImportColumn, List<String>> get() = custom.filterKeys { isCustom(it) }

    /** Index of [column] in [header] (titles already normalized), or -1. Earlier titles win. */
    fun find(column: ImportColumn, header: List<String>): Int {
        val lower = header.map { it.lowercase() }
        for (title in titles(column)) {
            val t = title.lowercase()
            val index = if (t.endsWith("*")) {
                val prefix = t.dropLast(1).trimEnd()
                lower.indexOfFirst { it.startsWith(prefix) }
            } else {
                lower.indexOf(t)
            }
            if (index >= 0) return index
        }
        return -1
    }

    companion object {
        val DEFAULT = ImportColumns()

        /** Same spacing rule as cells: trimmed, runs of spaces become one. */
        fun normalize(title: String?): String = title?.trim()?.replace(Regex("""\s+"""), " ").orEmpty()
    }
}
