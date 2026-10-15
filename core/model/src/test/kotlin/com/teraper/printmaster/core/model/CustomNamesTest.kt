package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomNamesTest {

    @Test
    fun englishWordsAreSwappedWholeInTheSameCase() {
        val names = CustomNames(
            TextLanguage.ENGLISH,
            mapOf(NamedRole.MASTER to RoleName("Courier"), NamedRole.ORDER to RoleName("Delivery", "Deliveries")),
        )
        assertEquals("Couriers and deliveries", names.apply("Masters and orders"))
        assertEquals("Done by the courier as his delivery", names.apply("Done by the master as his order"))
        assertEquals("DELIVERIES", names.apply("ORDERS"))
        // Not inside other words, and clients keep their name.
        assertEquals("PrintMaster client", names.apply("PrintMaster client"))
    }

    @Test
    fun armenianWordsKeepTheirEndings() {
        val names = CustomNames(TextLanguage.ARMENIAN, mapOf(NamedRole.MASTER to RoleName("Առաքիչ"), NamedRole.CLIENT to RoleName("Գնորդ", "Գնորդներ")))
        assertEquals("Առաքիչները", names.apply("Վարպետները"))
        assertEquals("Տվեք առաքիչին", names.apply("Տվեք վարպետին"))
        assertEquals("ԳՆՈՐԴՆԵՐ", names.apply("ՀԱՃԱԽՈՐԴՆԵՐ"))
        assertEquals("գնորդի հեռախոսը", names.apply("հաճախորդի հեռախոսը"))
    }

    @Test
    fun nothingSetChangesNothing() {
        val names = CustomNames(TextLanguage.ENGLISH, mapOf(NamedRole.CLIENT to RoleName("  ")))
        assertTrue(names.isEmpty)
        assertEquals("Clients", names.apply("Clients"))
        assertEquals(TextLanguage.ARMENIAN, TextLanguage.of("ru"))
    }
}
