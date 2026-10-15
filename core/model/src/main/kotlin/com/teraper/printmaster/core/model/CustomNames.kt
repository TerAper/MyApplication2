package com.teraper.printmaster.core.model

/*
 * The customer's own words for the app's main roles: "Master" can be a Deliverer or a Courier,
 * "Order" a Delivery, "Client" a Customer. Every text of the app is shown with them swapped in,
 * so nothing has to be written twice.
 */

/** The words a customer can rename. */
enum class NamedRole { MASTER, ORDER, CLIENT }

/** One role's word: [one] thing, [many] things. Blank = the app's own word. */
data class RoleName(val one: String = "", val many: String = "") {
    val isSet: Boolean get() = one.isNotBlank()
}

/** The languages the app's texts are written in; their own words differ. */
enum class TextLanguage {
    ARMENIAN, ENGLISH;

    /** The app's own words, lower case. */
    fun defaults(role: NamedRole): RoleName = when (this) {
        ARMENIAN -> when (role) {
            NamedRole.MASTER -> RoleName("վարպետ", "վարպետներ")
            NamedRole.ORDER -> RoleName("պատվեր", "պատվերներ")
            NamedRole.CLIENT -> RoleName("հաճախորդ", "հաճախորդներ")
        }
        ENGLISH -> when (role) {
            NamedRole.MASTER -> RoleName("master", "masters")
            NamedRole.ORDER -> RoleName("order", "orders")
            NamedRole.CLIENT -> RoleName("client", "clients")
        }
    }

    companion object {
        /** English texts for "en"; everything else is shown with the Armenian texts (the default). */
        fun of(languageCode: String?): TextLanguage = if (languageCode == "en") ENGLISH else ARMENIAN
    }
}

/** The customer's words in one [language]; roles not in [names] keep the app's words. */
data class CustomNames(val language: TextLanguage, val names: Map<NamedRole, RoleName> = emptyMap()) {

    private val swaps: List<Pair<String, String>> = buildList {
        for ((role, name) in names) {
            if (!name.isSet) continue
            val own = language.defaults(role)
            val many = name.many.trim().ifBlank { name.one.trim() + if (language == TextLanguage.ENGLISH) "s" else "ներ" }
            add(own.many to many)
            add(own.one to name.one.trim())
        }
    }.sortedByDescending { it.first.length }

    private val lookup: Map<String, String> = swaps.toMap()

    /**
     * Armenian adds endings to the word ("վարպետին", "պատվերները"), so a word is matched at its
     * start; English words are matched whole. Never inside another word: "PrintMaster" stays.
     */
    private val regex: Regex? = swaps.takeIf { it.isNotEmpty() }?.let { list ->
        val words = list.joinToString("|") { Regex.escape(it.first) }
        val end = if (language == TextLanguage.ENGLISH) "(?!\\p{L})" else ""
        Regex("(?<!\\p{L})($words)$end", RegexOption.IGNORE_CASE)
    }

    val isEmpty: Boolean get() = regex == null

    /** [text] with the customer's words, in the same case as the words they replace ("ORDERS" → "DELIVERIES"). */
    fun apply(text: String): String {
        val regex = regex ?: return text
        return regex.replace(text) { match ->
            val found = match.value
            val word = lookup.getValue(found.lowercase())
            when {
                found.length > 1 && found.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
                found.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
                else -> word.replaceFirstChar { it.lowercaseChar() }
            }
        }
    }
}
