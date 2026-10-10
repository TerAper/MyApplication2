package com.teraper.printmaster.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teraper.printmaster.core.data.repository.PrefsImportColumnsRepository
import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsImportColumnsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun clear() {
        context.getSharedPreferences("import_columns", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun changedTitlesSurviveARestartAndResetPerFile() = runTest {
        val first = PrefsImportColumnsRepository(context)
        first.setTitles(ImportColumn.BANK_PAYER, listOf("Վճարող/Շահառու", "Payer*"))
        first.setTitles(ImportColumn.INVOICE_AMOUNT, listOf("Amount"))

        // A new instance = the app opened again.
        val again = PrefsImportColumnsRepository(context)
        val loaded = again.observeColumns().first()
        assertEquals(listOf("Վճարող/Շահառու", "Payer*"), loaded.titles(ImportColumn.BANK_PAYER))
        assertEquals(listOf("Amount"), loaded.titles(ImportColumn.INVOICE_AMOUNT))

        again.reset(ImportKind.BANK_STATEMENT)
        val afterReset = PrefsImportColumnsRepository(context).observeColumns().first()
        assertEquals(setOf(ImportColumn.INVOICE_AMOUNT), afterReset.customized.keys)
        assertTrue(ImportColumn.BANK_PAYER !in afterReset.customized)
    }
}
