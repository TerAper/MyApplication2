package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportFilesTest {

    private val invoiceRows = listOf(
        listOf("Դուրս գրված հաշիվ վավերագրեր"),
        emptyList(),
        listOf("Հ/Հ", "Սերիա և համար", "Ստացողի ՀՎՀՀ", "Ստացողի անձը հաստատող փաստաթղթի սերիա և/կամ համար, ՀԾՀ", "Ստացողի անվանում", "Կարգավիճակ",
            "Դուրս գրման ա/թ", "Առաքման (տեղափոխման) ա/թ", "Ստացողի ստորագրման ա/թ", "Արժեք", "Դուրս գրողի ՀՎՀՀ", "Դուրս գրողի անվանում"),
        listOf("1", "B7031824772", "02920288", null, "«ԱՈՒԿՑԻՈՆ ՄԱՍԹԵՐՍ» (ՍՊԸ)", "Ենթակա է հաստատման", "45870.677", "45870", null, "4000", "86596363", "ՍՈԽԱԿ ՏԵՐ-ՍԱՐԳՍՅԱՆ (ԱՁ)"),
        listOf("2", "B6421358776", "01024073", null, "«ԷԼԻՏ ՌԵԶԻԴԵՆՍ» (ՓԲԸ)", "Հաստատված գնորդի կողմից", "45869.7", "45869", "45870.4", "4000.5", "86596363", "ՍՈԽԱԿ ՏԵՐ-ՍԱՐԳՍՅԱՆ (ԱՁ)"),
    )

    private val bankRows = listOf(
        listOf("Ժամանակահատվածի սկիզբ", "16/01/2025"),
        listOf("Հաշիվ N", "1150018363093024", "ՏԵՐ-ՍԱՐԳՍՅԱՆ ՍՈԽԱԿ ՄԵԽԱԿԻ ԱՁ"),
        listOf("ՀՎՀՀ", "1386596363"),
        emptyList(),
        listOf("Ամսաթիվ", "Փաստ.N", "ԳՏ", "Հաշիվ", "Նպատակ", "Մուտք", "Ելք", "Վճարող/Շահառու"),
        listOf("17/01/2025", "519302", "TRF", "220001154248000", "Հ/Վ  B5203593428 առ 08.01.2025", "4,000.00", "0.00", "\"ԻՆԹԵՐՆԵՅՇՆԼ ՄԵԴԻԱ ՀՈԼԴԻՆԳ\" ՍՊԸ"),
        listOf("23/01/2025", "1", "TRF", "900005", "Բյուջետային փոխանցում", "0.00", "900.00", "դրոշմանիշային վճար"),
        listOf("31/01/2025", "2", "INT", null, "% կապիտ.", "12.30", "0.00", null),
    )

    @Test
    fun invoiceExport() {
        val file = ImportFiles.parse(invoiceRows) as ImportFile.Invoices
        assertEquals("86596363", file.issuerTaxId)
        assertEquals(2, file.invoices.size)
        val first = file.invoices[0]
        assertEquals(LocalDate.of(2025, 8, 1), first.issued)
        assertEquals(Money.ofDram(4000), first.amount)
        assertEquals("02920288", first.clientTaxId)
        assertFalse(first.confirmedByBuyer)
        assertTrue(file.invoices[1].confirmedByBuyer)
        assertEquals(Money(400_050), file.invoices[1].amount)
        assertEquals("inv:B7031824772", first.fingerprint)
    }

    @Test
    fun bankStatementKeepsOnlyClientPayments() {
        val file = ImportFiles.parse(bankRows) as ImportFile.BankStatement
        assertEquals("1150018363093024", file.account)
        assertEquals("1386596363", file.ownerTaxId)
        assertEquals(2, file.skipped)
        val payment = file.payments.single()
        assertEquals(LocalDate.of(2025, 1, 17), payment.date)
        assertEquals(Money.ofDram(4000), payment.amount)
        assertEquals("220001154248000", payment.payerAccount)
        assertEquals(payment.fingerprint, payment.copy(purpose = "Հ/Վ B5203593428   առ 08.01.2025").fingerprint)
        assertNotEquals(payment.fingerprint, payment.copy(amount = Money.ofDram(4001)).fingerprint)
    }

    @Test
    fun otherFilesAndFormats() {
        assertNull(ImportFiles.parse(listOf(listOf("Name", "Phone"))))
        assertEquals(Money(400_050), ImportFiles.parseAmount("4 000,50"))
        assertEquals(Money.ofDram(1_234_567), ImportFiles.parseAmount("1,234,567"))
        assertTrue(ImportFiles.sameTaxId("1386596363", "86596363"))
        assertFalse(ImportFiles.sameTaxId("02920288", "86596363"))
    }
}
