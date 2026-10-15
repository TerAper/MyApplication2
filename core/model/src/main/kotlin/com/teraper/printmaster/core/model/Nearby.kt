package com.teraper.printmaster.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** How far to look for clients around where the user stands. */
enum class NearbyRadius(val meters: Int) { M500(500), KM1(1_000), KM2(2_000), KM5(5_000), KM10(10_000), KM25(25_000) }

/** A client's address on the map, and how far it is. */
data class NearbyClient(
    val clientId: Long,
    val clientName: String,
    val address: String,
    val point: MapPoint,
    val meters: Double,
)

/** A client's address and where it is on the map, when that's known. */
data class AddressPoint(
    val addressId: Long,
    val clientId: Long,
    val clientName: String,
    val address: String,
    val point: MapPoint?,
    /** Looked for and not found: typed so the map can't place it. */
    val notFound: Boolean = false,
)

object GeoMath {
    private const val EARTH_METERS = 6_371_000.0
    private val GEO = Regex("""^geo:(-?\d{1,2}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)""")

    /** Straight-line distance over the earth. */
    fun meters(a: MapPoint, b: MapPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_METERS * asin(sqrt(h))
    }

    /** The point of a "geo:lat,lng…" link (what picked or shared places are saved as), else null. */
    fun pointOf(link: String?): MapPoint? {
        val match = link?.trim()?.let(GEO::find) ?: return null
        val lat = match.groupValues[1].toDouble()
        val lng = match.groupValues[2].toDouble()
        return MapPoint(lat, lng).takeIf { lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0) }
    }

    /** Clients with an address within [radius] of [here], nearest first; one row per client (its nearest address). */
    fun nearby(here: MapPoint, addresses: List<AddressPoint>, radius: NearbyRadius): List<NearbyClient> =
        addresses.mapNotNull { a -> a.point?.let { NearbyClient(a.clientId, a.clientName, a.address, it, meters(here, it)) } }
            .filter { it.meters <= radius.meters }
            .groupBy { it.clientId }
            .map { (_, rows) -> rows.minBy { it.meters } }
            .sortedBy { it.meters }
}
