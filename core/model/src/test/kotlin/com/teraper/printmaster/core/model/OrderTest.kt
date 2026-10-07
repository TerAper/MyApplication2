package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OrderTest {
    private val day = LocalDate.of(2026, 10, 8)

    @Test
    fun clientAndDescriptionAreRequired() {
        assertEquals(setOf(OrderDraftError.CLIENT_REQUIRED, OrderDraftError.DESCRIPTION_REQUIRED), OrderDraft(date = day).validate())
        assertTrue(OrderDraft(clientId = 1, date = day, description = "Refill").validate().isEmpty())
    }

    @Test
    fun pickingClientTakesTheirFirstAddressAndPhone() {
        val client = Client(
            7, "Firm", ClientType.FIRM, null, "",
            phones = listOf(ClientPhone(3, "091", ""), ClientPhone(4, "010", "")),
            addresses = listOf(ClientAddress(5, "Komitas 5", "")),
        )
        val draft = OrderDraft(date = day, addressId = 99, phoneId = 98).withClient(client)
        assertEquals(7L, draft.clientId)
        assertEquals(5L, draft.addressId)
        assertEquals(3L, draft.phoneId)

        val noContacts = draft.withClient(client.copy(id = 8, phones = emptyList(), addresses = emptyList()))
        assertEquals(null, noContacts.addressId)
        assertEquals(null, noContacts.phoneId)
    }

    @Test
    fun statusActions() {
        assertEquals(listOf(OrderStatus.IN_PROGRESS, OrderStatus.DONE, OrderStatus.CANCELLED), OrderStatus.NEW.nextActions())
        assertEquals(listOf(OrderStatus.NEW), OrderStatus.DONE.nextActions())
        assertEquals(day.atTime(LocalTime.of(10, 0)), OrderDraft(date = day).scheduledAt)
    }
}
