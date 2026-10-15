package com.teraper.printmaster.core.data.nearby

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.teraper.printmaster.core.database.dao.AddressPlaceRow
import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.model.AddressPoint
import com.teraper.printmaster.core.model.GeoMath
import com.teraper.printmaster.core.model.MapPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Clients' addresses on the map: from their map links, or looked up once from the typed text. */
interface NearbyRepository {
    fun observeAddresses(): Flow<List<AddressPoint>>

    /**
     * Finds the places of addresses not looked up yet (or changed since), nearest to [near] first
     * when it's known; [onProgress] gets (done, total). Stops quietly without internet; what's
     * found is kept, so the next time only the rest is looked up.
     */
    suspend fun locate(near: MapPoint?, onProgress: (Int, Int) -> Unit = { _, _ -> }): LocateResult
}

enum class LocateResult { DONE, OFFLINE, NO_GEOCODER }

@Singleton
internal class GeocoderNearbyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clientDao: ClientDao,
) : NearbyRepository {

    override fun observeAddresses(): Flow<List<AddressPoint>> = clientDao.observeAddressPlaces().map { rows ->
        rows.map { row ->
            val current = row.geoKey == row.key()
            val lat = row.lat
            val lng = row.lng
            val point = GeoMath.pointOf(row.mapLink) ?: if (current && lat != null && lng != null) MapPoint(lat, lng) else null
            AddressPoint(row.addressId, row.clientId, row.clientName, row.address.ifBlank { row.mapLink.orEmpty() }, point, notFound = current && point == null)
        }
    }

    override suspend fun locate(near: MapPoint?, onProgress: (Int, Int) -> Unit): LocateResult {
        val todo = clientDao.observeAddressPlaces().first().filter { it.geoKey != it.key() }
        // Points from map links need no lookup.
        val (linked, typed) = todo.partition { GeoMath.pointOf(it.mapLink) != null }
        linked.forEach { row -> GeoMath.pointOf(row.mapLink)?.let { clientDao.setPlace(row.addressId, it.lat, it.lng, row.key()) } }
        val toLook = typed.filter { it.address.isNotBlank() }
        if (toLook.isEmpty()) return LocateResult.DONE
        if (!Geocoder.isPresent()) return LocateResult.NO_GEOCODER
        val geocoder = Geocoder(context, Locale.forLanguageTag("hy"))
        toLook.forEachIndexed { index, row ->
            onProgress(index, toLook.size)
            val found = try {
                withContext(Dispatchers.IO) { geocoder.find(row.address, near) }
            } catch (_: IOException) {
                return LocateResult.OFFLINE
            }
            clientDao.setPlace(row.addressId, found?.latitude, found?.longitude, row.key())
        }
        onProgress(toLook.size, toLook.size)
        return LocateResult.DONE
    }

    /** First around where the user is (the same street name exists in many towns), then anywhere. */
    private suspend fun Geocoder.find(text: String, near: MapPoint?): Address? {
        near?.let { lookup(text, it) }?.let { return it }
        return lookup(text, null)
    }

    private suspend fun Geocoder.lookup(text: String, near: MapPoint?): Address? {
        val box = near?.let { doubleArrayOf(it.lat - BOX, it.lng - BOX, it.lat + BOX, it.lng + BOX) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    }
                    if (box == null) getFromLocationName(text, 1, listener) else getFromLocationName(text, 1, box[0], box[1], box[2], box[3], listener)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            (if (box == null) getFromLocationName(text, 1) else getFromLocationName(text, 1, box[0], box[1], box[2], box[3]))?.firstOrNull()
        }
    }

    private companion object {
        /** About 75 km around the user. */
        const val BOX = 0.7
        const val TIMEOUT_MS = 10_000L
    }
}

/** What an address's place was found for: a changed text or link means looking again. */
private fun AddressPlaceRow.key() = "${address.trim()}|${mapLink.orEmpty()}"
