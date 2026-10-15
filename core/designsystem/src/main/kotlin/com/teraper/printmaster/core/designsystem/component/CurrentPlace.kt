package com.teraper.printmaster.core.designsystem.component

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Where the phone is: the street address when it can be found (else empty), and the exact point as a geo: link. */
data class CurrentPlace(val address: String, val mapLink: String)

/**
 * "Take my location": asks for location permission when needed, finds where the phone is and its
 * street address. Returns the function to call; [onResult] gets null when it couldn't be found
 * (no permission, location off, no fix in time).
 */
@Composable
fun rememberCurrentPlace(onResult: (CurrentPlace?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    val find = { scope.launch { result(context.currentPlace()) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) find() else result(null)
    }
    return {
        if (context.hasLocationPermission()) {
            find()
        } else {
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}

fun Context.hasLocationPermission(): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@SuppressLint("MissingPermission")
private suspend fun Context.currentPlace(): CurrentPlace? {
    if (!hasLocationPermission()) return null
    val client = LocationServices.getFusedLocationProviderClient(this)
    val location = runCatching {
        withTimeoutOrNull(20_000) { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await() }
            ?: client.lastLocation.await()
    }.getOrNull() ?: return null
    val address = withContext(Dispatchers.IO) { streetAddress(location.latitude, location.longitude) }
    return CurrentPlace(address.orEmpty(), "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
}

/** The street address at a point, in the app's language; null without internet or when unknown. */
private suspend fun Context.streetAddress(lat: Double, lng: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(this, resources.configuration.locales[0] ?: Locale.getDefault())
    val found: Address? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
        }
    }.getOrNull()
    found ?: return null
    // "Komitas 5, Yerevan" reads better than the full line with country and postcode.
    val street = listOfNotNull(found.thoroughfare, found.subThoroughfare).joinToString(" ")
    return listOf(street, found.locality.orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
        .ifBlank { found.getAddressLine(0) }
}
