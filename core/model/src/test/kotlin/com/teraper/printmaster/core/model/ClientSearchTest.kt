package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientSearchTest {

    private val firm = Client(
        id = 1,
        name = "«ԱԲԳ Սերվիս» ՍՊԸ",
        type = ClientType.FIRM,
        taxId = "01234567",
        note = "",
        phones = listOf(ClientPhone(1, "+374 91 12-34-56", "")),
        addresses = listOf(ClientAddress(1, "Երևան, Կոմիտասի 5", "")),
    )

    @Test
    fun `empty query matches everything`() {
        assertTrue(ClientSearch.matches(firm, "   "))
    }

    @Test
    fun `name match ignores case and quotes`() {
        assertTrue(ClientSearch.matches(firm, "աբգ"))
        assertTrue(ClientSearch.matches(firm, "ԱԲԳ ՍԵՐՎԻՍ"))
        assertTrue(ClientSearch.matches(firm, "սերվիս սպը"))
        assertFalse(ClientSearch.matches(firm, "փբը"))
    }

    @Test
    fun `tax id and address match`() {
        assertTrue(ClientSearch.matches(firm, "0123"))
        assertTrue(ClientSearch.matches(firm, "կոմիտաս"))
    }

    @Test
    fun `phone matches in any format`() {
        assertTrue(ClientSearch.matches(firm, "091123456"))
        assertTrue(ClientSearch.matches(firm, "91 12 34"))
        assertTrue(ClientSearch.matches(firm, "+37491123456"))
        assertFalse(ClientSearch.matches(firm, "093"))
    }

    @Test
    fun `normalizePhone strips country code and leading zero`() {
        assertEquals("91123456", ClientSearch.normalizePhone("+374 (91) 12-34-56"))
        assertEquals("91123456", ClientSearch.normalizePhone("091 123456"))
        assertEquals("91123456", ClientSearch.normalizePhone("0037491123456"))
    }
}
