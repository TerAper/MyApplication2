package com.teraper.printmaster.core.data.repository

import android.content.Context
import androidx.core.content.edit
import com.teraper.printmaster.core.model.ClientSort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How the client list is sorted, kept between launches. */
interface ClientListSettingsRepository {
    fun observeSort(): Flow<ClientSort>

    fun setSort(sort: ClientSort)
}

@Singleton
internal class PrefsClientListSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ClientListSettingsRepository {

    private val prefs = context.getSharedPreferences("client_list", Context.MODE_PRIVATE)
    private val sort = MutableStateFlow(
        prefs.getString(SORT, null)?.let { name -> ClientSort.entries.firstOrNull { it.name == name } } ?: ClientSort.NAME,
    )

    override fun observeSort(): Flow<ClientSort> = sort.asStateFlow()

    override fun setSort(sort: ClientSort) {
        this.sort.value = sort
        prefs.edit { putString(SORT, sort.name) }
    }

    private companion object {
        const val SORT = "sort"
    }
}
