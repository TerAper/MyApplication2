package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientDraftTest {

    @Test
    fun `name is required`() {
        assertEquals(setOf(ClientDraftError.NAME_REQUIRED), ClientDraft(name = "  ").validate())
    }

    @Test
    fun `firm tax id must be eight digits, spaces allowed`() {
        assertTrue(ClientDraft(name = "Firm", taxId = "0123 4567").validate().isEmpty())
        assertTrue(ClientDraft(name = "Firm", taxId = "").validate().isEmpty())
        assertEquals(setOf(ClientDraftError.TAX_ID_FORMAT), ClientDraft(name = "Firm", taxId = "1234").validate())
        assertEquals(setOf(ClientDraftError.TAX_ID_FORMAT), ClientDraft(name = "Firm", taxId = "12345678A").validate())
    }

    @Test
    fun `private client ignores tax id`() {
        val draft = ClientDraft(name = "Anna", type = ClientType.PRIVATE, taxId = "bad")
        assertTrue(draft.validate().isEmpty())
        assertNull(draft.normalizedTaxId())
        assertEquals("", draft.normalized().taxId)
    }

    @Test
    fun `normalized trims and drops empty contact rows`() {
        val draft = ClientDraft(
            name = "  «ԱԲԳ»   ՍՊԸ ",
            taxId = "0123 4567",
            phones = listOf(
                ClientDraft.ContactDraft(value = " 091 123456 ", label = " Office "),
                ClientDraft.ContactDraft(value = "   "),
            ),
            addresses = listOf(ClientDraft.ContactDraft()),
        ).normalized()

        assertEquals("«ԱԲԳ» ՍՊԸ", draft.name)
        assertEquals("01234567", draft.taxId)
        assertEquals(listOf(ClientDraft.ContactDraft(value = "091 123456", label = "Office")), draft.phones)
        assertTrue(draft.addresses.isEmpty())
    }

    @Test
    fun `from client keeps ids and adds an empty row when there are no contacts`() {
        val client = Client(
            id = 7, name = "Firm", type = ClientType.FIRM, taxId = "01234567", note = "",
            phones = listOf(ClientPhone(3, "091123456", "Office")),
            addresses = emptyList(),
        )
        val draft = ClientDraft.from(client)
        assertEquals(listOf(ClientDraft.ContactDraft(3, "091123456", "Office")), draft.phones)
        assertEquals(listOf(ClientDraft.ContactDraft()), draft.addresses)
    }
}
