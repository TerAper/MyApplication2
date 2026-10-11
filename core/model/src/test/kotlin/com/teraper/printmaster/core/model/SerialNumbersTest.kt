package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SerialNumbersTest {

    @Test
    fun labelWordsAndSpacingDontMatter() {
        assertEquals("CNB8K12345", SerialNumbers.clean("S/N: CNB8K12345"))
        assertEquals("CNB8K12345", SerialNumbers.clean("  SN CNB8K12345 "))
        assertEquals("X5RT 004417", SerialNumbers.clean("Serial No. X5RT   004417"))
        // A serial that itself starts with SN is kept whole.
        assertEquals("SN12345678", SerialNumbers.clean("SN12345678"))
        assertEquals("CNB8K12345", SerialNumbers.key("cnb8 k1-2345"))
        assertEquals(SerialNumbers.key("S/N:CNB8K12345"), SerialNumbers.key("cnb8k12345"))
    }
}
