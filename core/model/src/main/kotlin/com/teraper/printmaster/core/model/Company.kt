package com.teraper.printmaster.core.model

import java.util.Locale

/**
 * Kind of registration. Everyone registers as [OWNER] now: one or more own companies, doing the
 * work themselves or attaching masters. [MASTER] and [COMPANY] are older registrations, treated
 * the same as [OWNER].
 */
enum class AccountMode {
    /** Older: one master who works through one or more companies. */
    MASTER,

    /** Older: one company with one or more masters. */
    COMPANY,

    /** A master who joined a company's shared space: sees and finishes the orders it sends. */
    JOINED,

    OWNER,
    ;

    /** Has own companies with money (everyone except an older joined-only master). */
    val isOwner: Boolean get() = this != JOINED
}

/** Who uses the app; null in the repository until registration is done. */
data class AppProfile(
    val mode: AccountMode,
    /** The master's name (MASTER) or empty (COMPANY, whose name is the company's). */
    val ownerName: String,
    val defaultCompanyId: Long,
)

/**
 * A company money goes through: invoices are issued by it and bank transfers come to it.
 * Shown as [initials] on a color from a fixed palette ([colorIndex]).
 */
data class Company(
    val id: Long,
    val name: String,
    val taxId: String? = null,
    val bankAccounts: List<String> = emptyList(),
    val colorIndex: Int = 0,
) {
    val initials: String get() = CompanyNames.initials(name)
}

/** A person doing the work. In MASTER mode there is one: the user. */
data class Master(val id: Long, val name: String, val phone: String = "")

object CompanyNames {
    private val LEGAL_FORMS = setOf("սպը", "փբը", "բբը", "աձ", "llc", "cjsc", "ojsc", "ie")

    /**
     * Two letters for the badge: «ԱԲԳ Սերվիս» ՍՊԸ → "ԱՍ", "Alfa" → "AL".
     * Quotes and legal forms (ՍՊԸ, LLC, …) are skipped.
     */
    fun initials(name: String): String {
        val words = ClientSearch.normalizeText(name).split(' ')
            .filter { it.isNotEmpty() && it !in LEGAL_FORMS }
            .ifEmpty { return "?" }
        val letters = if (words.size == 1) words[0].take(2) else "${words[0].first()}${words[1].first()}"
        return letters.uppercase(Locale.ROOT)
    }
}

/** The company form. [id] 0 = new. */
data class CompanyDraft(
    val id: Long = 0,
    val name: String = "",
    val taxId: String = "",
    /** One account per line or separated by commas. */
    val bankAccounts: String = "",
    val colorIndex: Int = 0,
) {
    val isNew: Boolean get() = id == 0L

    fun validate(): Set<CompanyDraftError> = buildSet {
        if (name.isBlank()) add(CompanyDraftError.NAME_REQUIRED)
        val digits = normalizedTaxId()
        if (digits != null && !TAX_ID.matches(digits)) add(CompanyDraftError.TAX_ID_FORMAT)
        if (accountList().any { !ACCOUNT.matches(it) }) add(CompanyDraftError.BANK_ACCOUNT_FORMAT)
    }

    fun normalizedTaxId(): String? = taxId.filterNot { it.isWhitespace() }.ifEmpty { null }

    /** Bank accounts without spaces or dashes, so they compare with statement files. */
    fun accountList(): List<String> = bankAccounts.split(',', ';', '\n')
        .map { it.filterNot { c -> c.isWhitespace() || c == '-' } }
        .filter { it.isNotEmpty() }
        .distinct()

    companion object {
        private val TAX_ID = Regex("""\d{8}""")

        /** Armenian accounts are 16 digits; allow 10–20 for other formats. */
        private val ACCOUNT = Regex("""\d{10,20}""")

        fun from(company: Company) = CompanyDraft(
            id = company.id,
            name = company.name,
            taxId = company.taxId.orEmpty(),
            bankAccounts = company.bankAccounts.joinToString("\n"),
            colorIndex = company.colorIndex,
        )
    }
}

enum class CompanyDraftError { NAME_REQUIRED, TAX_ID_FORMAT, BANK_ACCOUNT_FORMAT }
