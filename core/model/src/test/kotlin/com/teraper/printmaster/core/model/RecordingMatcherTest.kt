package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingMatcherTest {

    private val matcher = RecordingMatcher(
        clientPhones = listOf(1L to "+374 77 001020", 2L to "091 123456", 3L to "093-55-66-77"),
        clientNames = listOf(1L to "Apo Xerox", 2L to "Atec", 4L to "Davit"),
        contacts = listOf(
            "Apo" to "077001020",
            "Atec1" to "+37491123456",
            "Davtashen Mankapartezi Dimac Epson L 1300 (office)" to "093556677",
            "Unknown Friend" to "099000000",
        ),
    )

    @Test
    fun aNumberMatchesTheClientsWithIt() {
        assertEquals(setOf(1L), matcher.clientsOf("077001020"))
        assertEquals(setOf(1L), matcher.clientsOf("+37477001020"))
        assertEquals(emptySet<Long>(), matcher.clientsOf("055000000"))
    }

    @Test
    fun aPhoneBookNameMatchesThroughTheContactsNumbers() {
        assertEquals(setOf(1L), matcher.clientsOf("Apo"))
        assertEquals(setOf(2L), matcher.clientsOf("Atec1"))
        // Brackets can't be in the file name, and the long name was cut.
        assertEquals(setOf(3L), matcher.clientsOf("Davtashen Mankapartezi Dimac Epson L 1300"))
        assertEquals(setOf(3L), matcher.clientsOf("Davtashen Mankapartezi Dimac"))
        // A contact whose number no client has.
        assertEquals(emptySet<Long>(), matcher.clientsOf("Unknown Friend"))
    }

    @Test
    fun withoutAContactTheClientsOwnNameCounts() {
        assertEquals(setOf(4L), matcher.clientsOf("Davit"))
        // Too short to be taken as the start of a longer contact name.
        assertEquals(emptySet<Long>(), matcher.clientsOf("Davt"))
    }
}
