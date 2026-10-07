package com.teraper.printmaster.feature.clients.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.teraper.printmaster.feature.clients.R

/** Opens the phone dialer with the number filled in (no call permission needed). */
internal fun Context.dial(number: String) =
    launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number.trim()))))

/** Opens a maps app searching for the address. */
internal fun Context.openMap(address: String) =
    launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.feature_clients_no_app, Toast.LENGTH_SHORT).show()
    }
}
