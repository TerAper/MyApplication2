package com.teraper.printmaster.core.model

import java.util.Locale

/**
 * Client search done in Kotlin rather than SQL: SQLite only lower-cases ASCII,
 * so `LIKE` can't match "ա" against "Ա". Client lists are small (hundreds), so
 * filtering in memory is instant.
 */
object ClientSearch {

    private val PUNCTUATION = Regex("""[«»"'“”„.,()\-_/\\]""")
    private val SPACES = Regex("""\s+""")
    private const val PHONE_SYMBOLS = " +-()"

    /** Lower-case, no quotes or punctuation, single spaces: «ԱԲԳ» ՍՊԸ → "աբգ սպը". */
    fun normalizeText(text: String): String =
        text.lowercase(Locale.ROOT).replace(PUNCTUATION, " ").replace(SPACES, " ").trim()

    /**
     * Digits of a phone number in national form, so "+374 91 123456", "091-12-34-56"
     * and "91123456" all compare equal.
     */
    fun normalizePhone(text: String): String {
        val digits = text.filter { it.isDigit() }
        return when {
            digits.startsWith("374") && digits.length > 8 -> digits.removePrefix("374")
            digits.startsWith("00374") -> digits.removePrefix("00374")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
    }

    /** True when every word of [query] is found in the client's name, ՀՎՀՀ, phones, addresses or note. */
    fun matches(client: Client, query: String): Boolean {
        val words = normalizeText(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true

        val haystack = buildString {
            append(normalizeText(client.name)).append(' ')
            client.taxId?.let { append(it).append(' ') }
            client.addresses.forEach { append(normalizeText(it.address)).append(' ') }
            append(normalizeText(client.note))
        }
        val phones = client.phones.map { normalizePhone(it.number) }

        // "091 12 34 56" is one phone number, not four words.
        if (query.isNotBlank() && query.all { it.isDigit() || it in PHONE_SYMBOLS }) {
            val digits = query.filter { it.isDigit() }
            if (phoneMatches(phones, digits) || digits in haystack) return true
        }

        return words.all { word ->
            word in haystack || (word.any { it.isDigit() } && phoneMatches(phones, word))
        }
    }

    private fun phoneMatches(phones: List<String>, word: String): Boolean {
        val digits = normalizePhone(word)
        return digits.length >= 3 && phones.any { digits in it }
    }
}
