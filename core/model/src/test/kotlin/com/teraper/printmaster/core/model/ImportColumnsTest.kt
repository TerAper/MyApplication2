package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportColumnsTest {

    // The bank renamed "Վճարող/Շահառու" to "Payer name" and "Մուտք" to "Credit".
    private val renamedBank = listOf(
        listOf("Account No", "1150018363093024", "ՏԵՐ-ՍԱՐԳՍՅԱՆ ԱՁ"),
        listOf("Ամսաթիվ", "Փաստ.N", "ԳՏ", "Հաշիվ", "Նպատակ", "Credit", "Ելք", "Payer name"),
        listOf("17/01/2025", "519302", "TRF", "220001154248000", "Հ/Վ B5203593428", "4,000.00", "0.00", "ԻՆԹԵՐՆԵՅՇՆԼ ՍՊԸ"),
    )

    @Test
    fun renamedColumnsAreReportedThenReadOnceSet() {
        assertNull(ImportFiles.parse(renamedBank))
        val missing = ImportFiles.missingColumns(renamedBank)!!
        assertEquals(ImportKind.BANK_STATEMENT, missing.kind)
        assertEquals(listOf(ImportColumn.BANK_INCOMING, ImportColumn.BANK_PAYER), missing.missing)

        val columns = ImportColumns()
            .with(ImportColumn.BANK_INCOMING, listOf("Մուտք", "credit"))
            .with(ImportColumn.BANK_PAYER, listOf("  Payer*  ", ""))
            .with(ImportColumn.BANK_OWN_ACCOUNT, listOf("Account No"))
        val file = ImportFiles.parse(renamedBank, columns) as ImportFile.BankStatement
        val payment = file.payments.single()
        assertEquals("ԻՆԹԵՐՆԵՅՇՆԼ ՍՊԸ", payment.payerName)
        assertEquals(Money.ofDram(4000), payment.amount)
        assertEquals(LocalDate.of(2025, 1, 17), payment.date)
        assertEquals("1150018363093024", file.account)
        assertNull(ImportFiles.missingColumns(renamedBank, columns))
    }

    @Test
    fun defaultsAreNotSavedAsChanges() {
        val columns = ImportColumns()
            .with(ImportColumn.BANK_PAYER, listOf("Վճարող/Շահառու", "Վճարող"))
            .with(ImportColumn.INVOICE_AMOUNT, listOf("Amount"))
        assertFalse(columns.isCustom(ImportColumn.BANK_PAYER))
        assertTrue(columns.isCustom(ImportColumn.INVOICE_AMOUNT))
        assertEquals(setOf(ImportColumn.INVOICE_AMOUNT), columns.customized.keys)

        val cleared = columns.with(ImportColumn.INVOICE_AMOUNT, listOf(" ", "*"))
        assertEquals(listOf("Արժեք"), cleared.titles(ImportColumn.INVOICE_AMOUNT))
        assertTrue(columns.reset(ImportKind.INVOICES).customized.isEmpty())
    }

    @Test
    fun exactTitleWinsOverStartsWith() {
        val header = listOf("Արժեք առանց ԱԱՀ", "Արժեք")
        val columns = ImportColumns().with(ImportColumn.INVOICE_AMOUNT, listOf("Արժեք"))
        assertEquals(1, columns.find(ImportColumn.INVOICE_AMOUNT, header))
        assertEquals(0, ImportColumns().with(ImportColumn.INVOICE_AMOUNT, listOf("Արժեք*")).find(ImportColumn.INVOICE_AMOUNT, header))
        assertEquals(-1, ImportColumns().find(ImportColumn.BANK_PAYER, header))
    }

    @Test
    fun aFileOfNeitherKindIsNotDiagnosed() {
        assertNull(ImportFiles.missingColumns(listOf(listOf("Name", "Phone"))))
    }
}
