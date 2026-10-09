package com.teraper.printmaster.core.data.repository

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.teraper.printmaster.core.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app language. Android 13+ keeps it itself (LocaleManager); on Android 12 the choice
 * is saved here and applied by the activity through [wrap].
 */
@Singleton
internal class AndroidLanguageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanguageRepository {

    // Bumped after a change; the phone's settings app can also change it, so it's re-read each time.
    private val changes = MutableStateFlow(0)

    override val needsRecreate: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    override fun observeLanguage(): Flow<AppLanguage> = changes.map { current() }

    override fun setLanguage(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            prefs(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
        }
        changes.value++
    }

    private fun current(): AppLanguage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)
            ?.let { AppLanguage.fromTag(it.toLanguageTag()) } ?: AppLanguage.SYSTEM
    } else {
        AppLanguage.fromTag(prefs(context).getString(KEY_LANGUAGE, null))
    }

    companion object {
        private const val KEY_LANGUAGE = "language"

        private fun prefs(context: Context) = context.getSharedPreferences("language", Context.MODE_PRIVATE)

        /** For Activity.attachBaseContext: applies the saved language on Android 12. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = prefs(base).getString(KEY_LANGUAGE, null) ?: return base
            val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
            return base.createConfigurationContext(config)
        }
    }
}
