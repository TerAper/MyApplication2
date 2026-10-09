package com.teraper.printmaster.core.model

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Bank payer names compared with client names. The bank writes them differently from the
 * invoice system: other quotes, the payer's own bank glued on, cut off, written twice,
 * abbreviated, words in another order. Each step below handles one of those.
 */
object PayerNames {

    private val LEGAL = setOf(
        "սպը", "փբը", "բբը", "աձ", "հկ", "հմդ", "հոակ", "պոակ", "կս", "հիմնադրամ", "կուսակցություն", "ընկերություն", "ընկերության",
        "մասնաճյուղ", "llc", "cjsc", "ojsc", "ie", "ngo", "ltd", "սահմանափակ", "պատասխանատվությամբ", "փակ", "բաժնետիրական",
        "անսահմանափակ", "հասարակական", "կազմակերպություն", "կազմակերպության", "պետական", "ոչ", "առեվտրային", "կառավարչական", "հիմնարկ", "մ/ճ",
    )
    private val ABBREVIATIONS = mapOf("անվ" to "անվան", "հիմն" to "հիմնական", "դպր" to "դպրոց", "ավ" to "ավագ", "եր" to "երեվանի")
    private val NUMBER_WORDS = setOf("թիվ", "թ", "հ", "h", "n", "№")
    private val GARBLED_QUOTES = Regex("""(?<!\S)մ(?=[Ա-Ֆ0-9])(.+?)Մ(?=\s|$)""")
    private val NUMBER = Regex("""(?:^|\s)(?:թիվ|թ|հ|h|n|№|#)?\s*\.?\s*(\d{1,4})(?!\d)""")
    private val PUNCTUATION = Regex("""[«»"“”„'(),.:;/\\\-]""")

    data class Normalized(val words: List<String>, val numbers: Set<String>) {
        /** Stable key for remembering a confirmed bank name. */
        val key: String get() = (words + numbers.sorted().map { "#$it" }).joinToString(" ")
    }

    fun normalize(name: String): Normalized {
        // The bank's export writes « » as a small մ and a capital Մ: "մ3Դ ՔՐԱՖՏՄ ՍՊԸ".
        var s = GARBLED_QUOTES.replace(name) { "«${it.groupValues[1]}»" }
        s = s.lowercase().replace("և", "եվ").replace("․", ".")
        // Written twice, glued: "ԹԻՎ 112 ԴՊՐՈՑ ՊՈԱԿ ԹԻՎ 112 ԴՊՐՈՑ ՊՈԱԿ".
        val head = s.trim().take(10)
        if (head.length == 10) s.indexOf(head, 10).takeIf { it > 0 }?.let { s = s.substring(0, it) }
        // The payer's bank after the payer's legal form: "ՍՊԸ «ԱյԴի Բանկ» ՓԲԸ «Կենտրոն» մ/ճ".
        val words = s.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val bankAt = words.indexOfFirst { "բանկ" in it || "bank" in it }
        val kept = if (bankAt > 0) {
            var cut = bankAt
            while (cut > 0 && words[cut - 1].trim('«', '»', '"', '(', ')') !in LEGAL) cut--
            words.take(if (cut > 0) cut else bankAt)
        } else {
            words
        }
        val text = kept.joinToString(" ")
        val numbers = NUMBER.findAll(PUNCTUATION.replace(text, " ")).map { it.groupValues[1] }.toSet()
        val tokens = PUNCTUATION.replace(text, " ").split(' ').mapNotNull { raw ->
            val word = ABBREVIATIONS[raw] ?: raw
            when {
                word.isEmpty() || word in LEGAL || word in NUMBER_WORDS || word.all { it.isDigit() } -> null
                // A legal form cut short: "ԸՆԿԵՐՈՒԹ".
                word.length >= 5 && LEGAL.any { it.startsWith(word) } -> null
                else -> word
            }
        }
        return Normalized(tokens, numbers)
    }

    internal fun wordSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        // The bank cuts long names: "ԿԵՆՏ" for "ԿԵՆՏՐՈՆ".
        if (a.length >= 3 && b.startsWith(a) || b.length >= 3 && a.startsWith(b)) return 0.9
        val longest = max(a.length, b.length)
        if (longest < 4) return 0.0
        val ratio = 1 - levenshtein(a, b).toDouble() / longest
        return if (ratio >= 0.75) ratio else 0.0
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }
}

/** Ranks clients for a payer name. Words common to many clients ("ԴՊՐՈՑ", "ԵՐԵՎԱՆԻ") count less. */
class PayerNameIndex(clients: Map<Long, String>) {

    private val normalized = clients.mapValues { PayerNames.normalize(it.value) }
    private val wordWeight: (String) -> Double

    init {
        val documents = normalized.values.map { it.words.toSet() }
        val frequency = HashMap<String, Int>()
        documents.forEach { words -> words.forEach { frequency[it] = (frequency[it] ?: 0) + 1 } }
        val total = documents.size
        wordWeight = { word -> ln((total + 1.0) / ((frequency[word] ?: 0) + 1)) + 1 }
    }

    fun score(payerName: String, clientId: Long): Double {
        val client = normalized[clientId] ?: return 0.0
        return score(PayerNames.normalize(payerName), client)
    }

    /** Best clients first, with scores 0..1. */
    fun rank(payerName: String): List<Pair<Long, Double>> {
        val payer = PayerNames.normalize(payerName)
        return normalized.map { (id, client) -> id to score(payer, client) }.sortedByDescending { it.second }
    }

    private fun score(payer: PayerNames.Normalized, client: PayerNames.Normalized): Double {
        if (payer.words.isEmpty() || client.words.isEmpty()) return 0.0
        val sameNumber = payer.numbers.intersect(client.numbers).isNotEmpty()
        // School No. 112 is not school No. 156.
        if (payer.numbers.isNotEmpty() && client.numbers.isNotEmpty() && !sameNumber) return 0.0
        val payerCovered = covered(payer.words, client.words)
        val clientCovered = covered(client.words, payer.words)
        // One name may hold extra words (district, patronymic): trust the shorter one more.
        val (shorter, longer) = if (payer.words.size <= client.words.size) payerCovered to clientCovered else clientCovered to payerCovered
        var score = 0.75 * shorter + 0.25 * longer
        if (sameNumber) score = min(1.0, score + 0.15) else if (payer.numbers != client.numbers) score *= 0.85
        return score
    }

    private fun covered(words: List<String>, other: List<String>): Double {
        val total = words.sumOf(wordWeight)
        if (total == 0.0) return 0.0
        return words.sumOf { word -> wordWeight(word) * (other.maxOfOrNull { PayerNames.wordSimilarity(word, it) } ?: 0.0) } / total
    }
}

/** Why a payment went to a client. */
enum class MatchReason { ACCOUNT, ALIAS, INVOICE, NAME, MANUAL }

sealed interface PaymentMatch {
    /** Sure enough to attach without asking. */
    data class Auto(val clientId: Long, val reason: MatchReason) : PaymentMatch

    /** Probably this client; the user confirms with one tap. */
    data class Suggested(val clientId: Long, val reason: MatchReason) : PaymentMatch

    data object None : PaymentMatch
}

/**
 * What the app knows from earlier imports and confirmations.
 * [accounts]: payer account → client (null = the account paid for different clients, so it's not trusted).
 */
data class MatchMemory(
    val aliases: Map<String, Long> = emptyMap(),
    val accounts: Map<String, Long?> = emptyMap(),
    /** Invoice number → client, from imported invoices. */
    val invoiceClients: Map<String, Long> = emptyMap(),
    /** Client → amounts of their invoices. */
    val invoiceAmounts: Map<Long, Set<Money>> = emptyMap(),
    /** Payer name key → clients the user said this payer is NOT. Never matched to them again. */
    val rejected: Map<String, Set<Long>> = emptyMap(),
)

class PaymentMatcher(clients: Map<Long, String>, private val memory: MatchMemory) {

    private val index = PayerNameIndex(clients)

    fun match(payment: ImportedPayment): PaymentMatch {
        val nameKey = PayerNames.normalize(payment.payerName).key
        val rejected = memory.rejected[nameKey].orEmpty()
        payment.payerAccount?.let { account ->
            memory.accounts[account]?.takeIf { it !in rejected }?.let { return PaymentMatch.Auto(it, MatchReason.ACCOUNT) }
        }
        memory.aliases[nameKey]?.takeIf { it !in rejected }?.let { return PaymentMatch.Auto(it, MatchReason.ALIAS) }

        val ranked = index.rank(payment.payerName).filter { it.first !in rejected }
        val (bestId, bestScore) = ranked.firstOrNull()?.let { (id, score) -> id to score + amountBonus(id, payment.amount) } ?: (null to 0.0)
        val secondScore = ranked.getOrNull(1)?.second ?: 0.0

        // Clients often mistype the invoice number, so it only counts when the name agrees too.
        val invoiceClient = INVOICE_NUMBER.findAll(payment.purpose)
            .mapNotNull { memory.invoiceClients[it.value.replace(" ", "").uppercase()] }
            .firstOrNull { it !in rejected }
        if (invoiceClient != null && index.score(payment.payerName, invoiceClient) >= 0.45) {
            return PaymentMatch.Auto(invoiceClient, MatchReason.INVOICE)
        }

        if (bestId != null && bestScore >= AUTO_SCORE && bestScore - secondScore >= AUTO_LEAD) return PaymentMatch.Auto(bestId, MatchReason.NAME)
        if (bestId != null && bestScore >= SUGGEST_SCORE) return PaymentMatch.Suggested(bestId, MatchReason.NAME)
        if (invoiceClient != null) return PaymentMatch.Suggested(invoiceClient, MatchReason.INVOICE)
        return PaymentMatch.None
    }

    /** A client with an invoice of exactly this amount is a bit more likely. */
    private fun amountBonus(clientId: Long, amount: Money): Double = if (amount in memory.invoiceAmounts[clientId].orEmpty()) 0.1 else 0.0

    companion object {
        const val AUTO_SCORE = 0.8
        const val AUTO_LEAD = 0.15
        const val SUGGEST_SCORE = 0.45
        private val INVOICE_NUMBER = Regex("""[A-Za-z]\s?\d{10}""")
    }
}
