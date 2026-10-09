package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Real name pairs from a bank statement and the invoice export (bank name → invoice name). */
class PaymentMatcherTest {

    private val clients = mapOf(
        1L to "«3Դ ՔՐԱՖՏ» (ՍՊԸ)",
        2L to "«ԿՐԻՍՏԱԼ 7» (ՍՊԸ)",
        3L to "«ՍԱՄՎԵԼՅԱՆ ԴԻԶԱՅՆԻ ԵՎ ԱՐՎԵՍՏԻ ԿԵՆՏՐՈՆ» (ՍՊԸ)",
        4L to "ՅՈՒԼԻԱ ՔԱՐԱՄՅԱՆ (ԱՁ)",
        5L to "«ԵՐԵՎԱՆԻ Հ. 112 ԱՎԱԳ ԴՊՐՈՑ » (ՊՈԱԿ)",
        6L to "«ԵՐԵՎԱՆԻ ԶՈՐԱՅՐ ԽԱԼԱՓՅԱՆԻ ԱՆՎԱՆ Հ.156 ՀԻՄՆԱԿԱՆ ԴՊՐՈՑ» (ՊՈԱԿ)",
        7L to "«ԵՐԵՎԱՆԻ ՊԵՅՈ ՅԱՎՈՐՈՎԻ ԱՆՎԱՆ Հ.131 ՀԻՄՆԱԿԱՆ ԴՊՐՈՑ» (ՊՈԱԿ)",
        8L to "«ՄԲՄ» (ՍՊԸ)",
        9L to "«ՀԱՅԿԱԿԱՆ ՀՈԳԵՎՈՐ ՎԵՐԱԿԱՆԳՆՄԱՆ» ՀԻՄՆԱԴՐԱՄ Հիմնադրամ",
        10L to "«ԲԱՅՄԻ ԷՅ ԷՄ» ԱՆՍԱՀՄԱՆԱՓԱԿ ԸՆԿԵՐՈՒԹՅԱՆ ՀԱՅԱՍՏԱՆՅԱՆ ՄԱՍՆԱՃՅՈՒՂ",
        11L to "«Սիրիուս Կապիտալ» ՓԲԸ (ՓԲԸ)",
        12L to "«ՀԱՅԱՍՏԱՆԻ ՀԱՆՐԱՊԵՏՈՒԹՅԱՆ ՊԱՇՏՊԱՆՈՒԹՅԱՆ ՆԱԽԱՐԱՐՈՒԹՅՈՒՆ» Պետական կառավարչական հիմնարկ",
    )

    private fun payment(name: String, purpose: String = "", account: String? = null, amount: Long = 4000) =
        ImportedPayment(LocalDate.of(2025, 3, 1), "1", account, purpose, Money.ofDram(amount), name)

    private fun matcher(memory: MatchMemory = MatchMemory()) = PaymentMatcher(clients, memory)

    @Test
    fun bankWayOfWritingNamesStillMatches() {
        val cases = mapOf(
            "մ3Դ ՔՐԱՖՏՄ ՍՊԸ մԱյԴի ԲանկՄ ՓԲԸ մԱրաբկիրՄ մ/ճ" to 1L,
            "ԿՐԻՍՏԱԼ 7 ՍՊԸ մԱյԴի ԲանկՄ ՓԲԸ մԿենտրոնՄ մ/ճ" to 2L,
            "ՍԱՄՎԵԼՅԱՆ ԴԻԶԱՅՆԻ ԵՎ ԱՐՎԵՍՏԻ ԿԵՆՏ" to 3L,
            "ՔԱՐԱՄՅԱՆ ՅՈՒԼԻԱ ՍՈՒՐԵՆԻ ԱՁ" to 4L,
            "Պեյո Յավրովի անվ. հ.131 հիմն. դպր.Պեյո Յավրովի անվ. հ.131 հիմն." to 7L,
            "ՄԲՄ ՍՊԸ" to 8L,
            "«ՀԱՅԿԱԿԱՆ ՀՈԳԵՎՈՐ ՎԵՐԱԿԱՆԳՄԱՆ» ՀԻՄՆԱԴՐԱՄ ՀՄԴ" to 9L,
            "«ՍԻՐԻՈՒՍ ԿԱՊԻՏԱԼ» ՓԲԸ" to 11L,
        )
        cases.forEach { (bankName, clientId) ->
            assertEquals(bankName, PaymentMatch.Auto(clientId, MatchReason.NAME), matcher().match(payment(bankName)))
        }
    }

    @Test
    fun unsureNamesAreOnlySuggested() {
        val school = matcher().match(payment("թիվ 112 ավագ դպրոց ՊՈԱԿ թիվ 112 ավագ դպրոց ՊՈԱԿ"))
        assertEquals(5L, (school as? PaymentMatch.Auto)?.clientId ?: (school as PaymentMatch.Suggested).clientId)
        // Different school number: never the other school.
        val other = matcher().match(payment("Թիվ 157 հիմնական դպրոց"))
        assertTrue(other !is PaymentMatch.Auto)
        assertEquals(PaymentMatch.None, matcher().match(payment("Կարապետյան Հակոբ Ժորայի")))
    }

    @Test
    fun invoiceNumberCountsOnlyWhenTheNameAgrees() {
        val memory = MatchMemory(invoiceClients = mapOf("B5203593428" to 2L, "B0000000001" to 12L))
        assertEquals(PaymentMatch.Auto(2, MatchReason.INVOICE), matcher(memory).match(payment("ԿՐԻՍՏԱԼ 7", "Հ/Վ  B 5203593428 առ 08.01.2025")))
        // A mistyped number pointing at someone else: the name wins, the number doesn't.
        assertEquals(PaymentMatch.Auto(1, MatchReason.NAME), matcher(memory).match(payment("«3Դ ՔՐԱՖՏ» ՍՊԸ", "B5203593428")))
        // Military unit paying for the ministry: no name match, so only a suggestion.
        assertEquals(
            PaymentMatch.Suggested(12, MatchReason.INVOICE),
            matcher(memory).match(payment("Թիվ 56 ֆինանսական ապահովման բաժանմո ՀՀ ՊՆ 30573 զորամաս", "B0000000001")),
        )
    }

    @Test
    fun rememberedAccountAndAliasWin() {
        val name = "Թիվ 56 ֆինանսական ապահովման բաժանմո ՀՀ ՊՆ 30573 զորամաս"
        val alias = MatchMemory(aliases = mapOf(PayerNames.normalize(name).key to 12L))
        assertEquals(PaymentMatch.Auto(12, MatchReason.ALIAS), matcher(alias).match(payment(name)))
        val account = MatchMemory(accounts = mapOf("900005001" to 12L, "777" to null))
        assertEquals(PaymentMatch.Auto(12, MatchReason.ACCOUNT), matcher(account).match(payment("anything", account = "900005001")))
        // An account seen with different clients isn't trusted.
        assertEquals(PaymentMatch.Auto(8, MatchReason.NAME), matcher(account).match(payment("ՄԲՄ ՍՊԸ", account = "777")))
    }

    @Test
    fun aClientTheUserRejectedIsNeverMatchedAgain() {
        val name = "ԿՐԻՍՏԱԼ 7 ՍՊԸ մԱյԴի ԲանկՄ ՓԲԸ"
        val key = PayerNames.normalize(name).key
        val memory = MatchMemory(
            accounts = mapOf("555" to 2L),
            invoiceClients = mapOf("B5203593428" to 2L),
            rejected = mapOf(key to setOf(2L)),
        )
        val result = matcher(memory).match(payment(name, "B5203593428", account = "555"))
        assertTrue(result !is PaymentMatch.Auto || result.clientId != 2L)
        assertTrue(result !is PaymentMatch.Suggested || result.clientId != 2L)
    }
}
