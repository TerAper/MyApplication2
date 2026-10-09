package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RecordingNamesTest {

    @Test
    fun samsungRussianNames() {
        val apo = RecordingNames.parse("Запись вызовов Apo_261009_192003.m4a")!!
        assertEquals("Apo", apo.caller)
        assertEquals(LocalDateTime.of(2026, 10, 9, 19, 20, 3), apo.startedAt)
        assertFalse(apo.isNumber)

        assertEquals("Atec1", RecordingNames.parse("Запись вызовов Atec1_261009_202123.m4a")!!.caller)
        assertEquals(
            "Davtashen Mankapartezi Dimac Epson L 1300",
            RecordingNames.parse("Запись вызовов Davtashen Mankapartezi Dimac Epson L 1300_261009_173912.m4a")!!.caller,
        )
    }

    @Test
    fun numbersAreRecognised() {
        val unknown = RecordingNames.parse("Запись вызовов 077001020_261009_180042.m4a")!!
        assertTrue(unknown.isNumber)
        assertEquals("77001020", unknown.phone)
        assertEquals("77001020", RecordingNames.parse("Call recording +374 77 001020_261009_180042.m4a")!!.phone)
    }

    @Test
    fun namesWithoutPrefixAndOtherFiles() {
        assertEquals("Armen", RecordingNames.parse("Armen_261009_101500.m4a")!!.caller)
        assertEquals("Արմեն Սարգսյան", RecordingNames.parse("Запись вызовов Արմեն Սարգսյան_261009_101500.M4A")!!.caller)
        assertNull(RecordingNames.parse("Apo_261009_192003.jpg"))
        assertNull(RecordingNames.parse("Voice 001.m4a"))
        assertNull(RecordingNames.parse("Apo_261399_192003.m4a")) // month 13
    }

    @Test
    fun prefixOfAnotherLanguageIsLearned() {
        val files = listOf(
            "Nagrywanie rozmów Apo_261009_192003.m4a",
            "Nagrywanie rozmów Davtashen Dimac_261009_173912.m4a",
            "Nagrywanie rozmów 077001020_261009_180042.m4a",
        )
        val prefix = RecordingNames.learnPrefix(files)
        assertEquals("Nagrywanie rozmów", prefix)
        assertEquals("Apo", RecordingNames.parse(files[0], RecordingNames.KNOWN_PREFIXES + prefix!!)!!.caller)

        // All calls with one person: the name itself must not become the prefix.
        assertEquals("Call recording", RecordingNames.learnPrefix(List(3) { "Call recording Apo_26100${it}_192003.m4a" }))
        assertNull(RecordingNames.learnPrefix(listOf("Apo_261009_192003.m4a", "Atec_261009_192003.m4a", "Bob_261009_192003.m4a")))
    }
}
