package com.teraper.printmaster

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Published builds: Google Play vouches that requests come from the real app on a real phone. */
internal object AppCheck {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
