package com.teraper.printmaster.core.model

import java.net.URLDecoder
import java.util.Locale

/** A place shared to the app from a map app: what to write as the address, and how to open it again. */
data class SharedPlace(
    /** Address or place name; empty when the map shared only a point. */
    val address: String,
    /** "geo:40.1772,44.5035?q=40.1772,44.5035" when coordinates were found, else the map's link; null = neither. */
    val mapLink: String?,
)

/**
 * Reads what Yandex Maps (or Google Maps) puts into "Share": usually the place name and
 * address on separate lines plus a link, or "40.177200, 44.503490" for a dropped pin.
 * Yandex links write the point as longitude,latitude (ll=, pt=, whatshere[point]=).
 */
object SharedPlaces {

    private val URL = Regex("""https?://\S+""")
    private val LAT_LON_TEXT = Regex("""(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")
    private val YANDEX_POINT = Regex("""(?:whatshere\[point]|pt|ll)=(-?\d{1,3}\.\d+),(-?\d{1,2}\.\d+)""")
    private val GOOGLE_POINT = Regex("""[@=](-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""")

    fun parse(text: String): SharedPlace? {
        if (text.isBlank()) return null
        val urls = URL.findAll(text).map { it.value.trimEnd(')', '.', ',') }.toList()
        val point = urls.firstNotNullOfOrNull(::pointInUrl) ?: LAT_LON_TEXT.find(text)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }

        val address = text.lines()
            .map { line -> URL.replace(line, "").trim().trimEnd(',') }
            .filter { it.isNotEmpty() && !LAT_LON_TEXT.matches(it) }
            .distinct()
            .joinToString(", ")

        val link = point?.let { (lat, lon) -> geoLink(lat, lon) } ?: urls.firstOrNull()
        if (address.isEmpty() && link == null) return null
        return SharedPlace(address, link)
    }

    fun geoLink(lat: Double, lon: Double): String {
        val point = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
        return "geo:$point?q=$point"
    }

    /** Latitude, longitude from a map link, or null. */
    private fun pointInUrl(url: String): Pair<Double, Double>? {
        val decoded = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        if ("yandex" in decoded) {
            YANDEX_POINT.find(decoded)?.let { return it.groupValues[2].toDouble() to it.groupValues[1].toDouble() }
        }
        if ("google" in decoded) {
            GOOGLE_POINT.find(decoded)?.let { return it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
        }
        return null
    }
}
