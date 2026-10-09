package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.AppLanguage
import kotlinx.coroutines.flow.Flow

interface LanguageRepository {

    fun observeLanguage(): Flow<AppLanguage>

    /**
     * Android 13+ applies it at once (and shows it in the phone's app settings).
     * On older phones it is applied when the screen is recreated; [needsRecreate] tells the caller.
     */
    fun setLanguage(language: AppLanguage)

    /** True when the caller must recreate the activity to show the new language. */
    val needsRecreate: Boolean
}
