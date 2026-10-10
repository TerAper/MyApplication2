package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.ImportColumnsRepository
import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportColumns
import com.teraper.printmaster.core.model.ImportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeImportColumnsRepository(initial: ImportColumns = ImportColumns.DEFAULT) : ImportColumnsRepository {

    val columns = MutableStateFlow(initial)

    override fun observeColumns(): Flow<ImportColumns> = columns

    override suspend fun setTitles(column: ImportColumn, titles: List<String>) = columns.update { it.with(column, titles) }

    override suspend fun reset(kind: ImportKind) = columns.update { it.reset(kind) }
}
