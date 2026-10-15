package com.teraper.printmaster

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Test builds (emulator, phones installed from Android Studio): a debug key. The first start logs
 * "Enter this debug secret into the allow list…"; that key is added once in Firebase → App Check.
 */
internal object AppCheck {
    fun install(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
