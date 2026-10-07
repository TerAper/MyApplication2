package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyTest {

    @Test
    fun initialsSkipQuotesAndLegalForms() {
        assertEquals("ԱՍ", CompanyNames.initials("«Ալֆա Սերվիս» ՍՊԸ"))
        assertEquals("ԱԼ", CompanyNames.initials("«Ալֆա» ՍՊԸ"))
        assertEquals("BP", CompanyNames.initials("Beta Print LLC"))
        assertEquals("?", CompanyNames.initials("  "))
    }

    @Test
    fun draftValidation() {
        assertEquals(setOf(CompanyDraftError.NAME_REQUIRED), CompanyDraft().validate())
        assertEquals(setOf(CompanyDraftError.TAX_ID_FORMAT), CompanyDraft(name = "A", taxId = "123").validate())
        assertEquals(setOf(CompanyDraftError.BANK_ACCOUNT_FORMAT), CompanyDraft(name = "A", bankAccounts = "12-34").validate())
        assertTrue(CompanyDraft(name = "A", taxId = "0123 4567", bankAccounts = "2470-0000-1234-5678\n").validate().isEmpty())
    }

    @Test
    fun bankAccountsAreSplitAndCleaned() {
        val draft = CompanyDraft(name = "A", bankAccounts = "2470 0000 1234 5678, 1570000012345678\n2470000012345678")
        assertEquals(listOf("2470000012345678", "1570000012345678"), draft.accountList())
    }
}
