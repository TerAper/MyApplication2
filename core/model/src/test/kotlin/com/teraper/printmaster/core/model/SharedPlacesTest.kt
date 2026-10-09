package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPlacesTest {

    @Test
    fun yandexOrganization() {
        val place = SharedPlaces.parse(
            "Дом печати\nулица Комитаса, 5, Ереван\nhttps://yandex.ru/maps/org/dom_pechati/1234567/?ll=44.503490%2C40.177200&z=17",
        )!!
        assertEquals("Дом печати, улица Комитаса, 5, Ереван", place.address)
        assertEquals("geo:40.177200,44.503490?q=40.177200,44.503490", place.mapLink)
    }

    @Test
    fun yandexDroppedPin() {
        val place = SharedPlaces.parse(
            "40.177200, 44.503490\nhttps://yandex.ru/maps/?whatshere%5Bpoint%5D=44.503490%2C40.177200&whatshere%5Bzoom%5D=17&z=17",
        )!!
        assertEquals("", place.address)
        assertEquals("geo:40.177200,44.503490?q=40.177200,44.503490", place.mapLink)
    }

    @Test
    fun shortLinkIsKeptAsIs() {
        val place = SharedPlaces.parse("Комитас 5, Ереван https://yandex.ru/maps/-/CHuIRVje")!!
        assertEquals("Комитас 5, Ереван", place.address)
        assertEquals("https://yandex.ru/maps/-/CHuIRVje", place.mapLink)
    }

    @Test
    fun googleAndPlainText() {
        assertEquals(
            "geo:40.177200,44.503490?q=40.177200,44.503490",
            SharedPlaces.parse("https://www.google.com/maps/place/Komitas/@40.1772,44.50349,17z")!!.mapLink,
        )
        assertEquals(SharedPlace("Komitas 5, Yerevan", null), SharedPlaces.parse("Komitas 5, Yerevan"))
        assertNull(SharedPlaces.parse("   "))
    }
}
