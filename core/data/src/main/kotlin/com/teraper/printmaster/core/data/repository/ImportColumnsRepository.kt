package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportColumns
import com.teraper.printmaster.core.model.ImportKind
import kotlinx.coroutines.flow.Flow

/** The column titles the Excel import looks for (Settings → Excel columns). Kept on this phone. */
interface ImportColumnsRepository {

    fun observeColumns(): Flow<ImportColumns>

    /** Empty or the default titles = back to default. */
    suspend fun setTitles(column: ImportColumn, titles: List<String>)

    suspend fun reset(kind: ImportKind)
}
