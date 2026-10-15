package com.teraper.printmaster.core.data.repository

import android.content.Context
import com.teraper.printmaster.core.data.AppNames
import com.teraper.printmaster.core.model.CustomNames
import com.teraper.printmaster.core.model.TextLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The customer's own words for Master, Order and Client, per text language. */
interface NamesRepository {
    fun names(language: TextLanguage): CustomNames

    /** Shown everywhere once the screens are rebuilt. */
    fun save(names: CustomNames)
}

internal class PrefsNamesRepository @Inject constructor(@ApplicationContext private val context: Context) : NamesRepository {
    override fun names(language: TextLanguage): CustomNames = AppNames.read(context, language)

    override fun save(names: CustomNames) = AppNames.save(context, names)
}
