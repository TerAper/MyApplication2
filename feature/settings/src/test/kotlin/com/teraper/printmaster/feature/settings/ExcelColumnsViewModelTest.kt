package com.teraper.printmaster.feature.settings

import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportKind
import com.teraper.printmaster.core.testing.FakeImportColumnsRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.settings.columns.ExcelColumnsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExcelColumnsViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    @Test
    fun editSavesTitlesAndResetBringsDefaultsBack() = runTest {
        val repo = FakeImportColumnsRepository()
        val vm = ExcelColumnsViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onEdit(ImportColumn.BANK_PAYER)
        assertEquals("Վճարող/Շահառու\nՎճարող", vm.uiState.value.editing?.text)
        vm.onEditTextChange("Վճարող/Շահառու\nPayer*\n")
        vm.onSaveEdit()
        assertNull(vm.uiState.value.editing)
        assertEquals(listOf("Վճարող/Շահառու", "Payer*"), vm.uiState.value.columns.titles(ImportColumn.BANK_PAYER))
        assertTrue(vm.uiState.value.columns.isCustom(ImportColumn.BANK_PAYER))

        vm.onEdit(ImportColumn.BANK_PAYER)
        vm.onUseDefault()
        assertEquals("Վճարող/Շահառու\nՎճարող", vm.uiState.value.editing?.text)
        vm.onDismissEdit()
        assertTrue(vm.uiState.value.columns.isCustom(ImportColumn.BANK_PAYER))

        vm.onResetClick(ImportKind.BANK_STATEMENT)
        assertEquals(ImportKind.BANK_STATEMENT, vm.uiState.value.confirmReset)
        vm.onConfirmReset()
        assertFalse(vm.uiState.value.columns.isCustom(ImportColumn.BANK_PAYER))
        assertNull(vm.uiState.value.confirmReset)
    }
}
