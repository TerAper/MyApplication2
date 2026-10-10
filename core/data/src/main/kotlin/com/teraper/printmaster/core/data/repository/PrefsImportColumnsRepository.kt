package com.teraper.printmaster.core.data.repository

import android.content.Context
import androidx.core.content.edit
import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportColumns
import com.teraper.printmaster.core.model.ImportKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Only changed columns are saved, one key per column, titles one per line. */
@Singleton
internal class PrefsImportColumnsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportColumnsRepository {

    private val prefs by lazy { context.getSharedPreferences("import_columns", Context.MODE_PRIVATE) }
    private val columns = MutableStateFlow<ImportColumns?>(null)

    override fun observeColumns(): Flow<ImportColumns> = flow {
        load()
        columns.collect { it?.let { value -> emit(value) } }
    }

    override suspend fun setTitles(column: ImportColumn, titles: List<String>) = save { it.with(column, titles) }

    override suspend fun reset(kind: ImportKind) = save { it.reset(kind) }

    private suspend fun load(): ImportColumns {
        columns.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            ImportColumn.entries.fold(ImportColumns()) { acc, column ->
                prefs.getString(column.name, null)?.let { acc.with(column, it.split('\n')) } ?: acc
            }
        }
        columns.update { it ?: loaded }
        return columns.value!!
    }

    private suspend fun save(change: (ImportColumns) -> ImportColumns) {
        val updated = change(load())
        columns.value = updated
        withContext(Dispatchers.IO) {
            prefs.edit {
                clear()
                updated.customized.forEach { (column, titles) -> putString(column.name, titles.joinToString("\n")) }
            }
        }
    }
}
