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

/** Opens a maps app at [target]: a point or map link opens exactly, plain text is searched. */
internal fun Context.openMap(target: String) {
    val uri = if (target.startsWith("geo:") || target.startsWith("http")) target else "geo:0,0?q=" + Uri.encode(target)
    launch(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
}

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.feature_clients_no_app, Toast.LENGTH_SHORT).show()
    }
}
