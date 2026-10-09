package com.teraper.printmaster.core.data

import android.content.Context
import com.teraper.printmaster.core.data.repository.AndroidLanguageRepository

/** Used by the activity so the chosen language also works on Android 12. */
object AppLocale {
    fun wrap(base: Context): Context = AndroidLanguageRepository.wrap(base)
}
