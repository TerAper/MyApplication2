package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearbyTest {

    private val republicSquare = MapPoint(40.1777, 44.5126)
    private val cascade = MapPoint(40.1911, 44.5153)
    private val gyumri = MapPoint(40.7894, 43.8475)

    @Test
    fun distancesAreRealistic() {
        // Republic Square → Cascade is about 1.5 km; Yerevan → Gyumri about 89 km in a straight line.
        assertEquals(1_510.0, GeoMath.meters(republicSquare, cascade), 50.0)
        assertEquals(89_000.0, GeoMath.meters(republicSquare, gyumri), 2_000.0)
    }

    @Test
    fun pointsComeFromGeoLinksOnly() {
        assertEquals(MapPoint(40.1772, 44.5035), GeoMath.pointOf("geo:40.1772,44.5035?q=40.1772,44.5035"))
        assertNull(GeoMath.pointOf("https://yandex.com/maps/-/CDabc"))
        assertNull(GeoMath.pointOf("geo:0,0?q=Komitas"))
        assertNull(GeoMath.pointOf(null))
    }

    @Test
    fun nearbyKeepsEachClientsNearestAddressWithinTheRadius() {
        val addresses = listOf(
            AddressPoint(1, 10, "Firm A", "Office", cascade),
            AddressPoint(2, 10, "Firm A", "Warehouse", republicSquare),
            AddressPoint(3, 20, "Firm B", "Gyumri", gyumri),
            AddressPoint(4, 30, "Firm C", "Komitas", null, notFound = true),
        )
        val found = GeoMath.nearby(MapPoint(40.1780, 44.5130), addresses, NearbyRadius.KM2)
        assertEquals(listOf(10L to "Warehouse"), found.map { it.clientId to it.address })
        // Gyumri is far beyond 25 km; an address not on the map never shows.
        assertEquals(listOf(10L), GeoMath.nearby(republicSquare, addresses, NearbyRadius.KM25).map { it.clientId })
    }
}
