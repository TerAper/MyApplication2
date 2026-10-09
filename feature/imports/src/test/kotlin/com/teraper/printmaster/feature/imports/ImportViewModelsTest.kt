package com.teraper.printmaster.feature.imports

import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyCheck
import com.teraper.printmaster.core.model.ImportBatch
import com.teraper.printmaster.core.model.ImportKind
import com.teraper.printmaster.core.model.ImportPreview
import com.teraper.printmaster.core.model.ImportPreviewResult
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PendingPayment
import com.teraper.printmaster.core.testing.FakeImportRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.imports.file.ImportError
import com.teraper.printmaster.feature.imports.file.ImportUiState
import com.teraper.printmaster.feature.imports.file.ImportViewModel
import com.teraper.printmaster.feature.imports.history.HistoryViewModel
import com.teraper.printmaster.feature.imports.review.ReviewViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private fun preview(check: CompanyCheck) = ImportPreview(
        ImportKind.BANK_STATEMENT, "bank.xlsx", Company(1, "Alfa"), check, rows = 3, newRows = 3, newAmount = Money.ofDram(9_000),
    )

    @Test
    fun fileOfAnotherCompanyNeedsASecondYes() = runTest {
        val repo = FakeImportRepository(ImportPreviewResult.Ready(preview(CompanyCheck.DIFFERENT)))
        val vm = ImportViewModel(repo)
        vm.onFilePicked("content://f", "bank.xlsx")
        vm.onImportClick()
        assertTrue((vm.uiState.value as ImportUiState.Previewing).askDifferentCompany)
        assertTrue(repo.imported.isEmpty())

        vm.onDismissDifferentCompany()
        vm.onImportClick()
        vm.onConfirmDifferentCompany()
        assertEquals(1, repo.imported.size)
        assertTrue(vm.uiState.value is ImportUiState.Done)
    }

    @Test
    fun companyWithoutIdentityCanSaveTheFilesOne() = runTest {
        val repo = FakeImportRepository(ImportPreviewResult.Ready(preview(CompanyCheck.COMPANY_HAS_NONE)))
        val vm = ImportViewModel(repo)
        vm.onFilePicked("content://f", "bank.xlsx")
        vm.onSaveIdentityChange(false)
        vm.onImportClick()
        assertEquals(listOf(false), repo.imported)

        repo.previewResult = ImportPreviewResult.UnknownLayout
        vm.onStartOver()
        vm.onFilePicked("content://g", "x.xlsx")
        assertEquals(ImportUiState.Failed(ImportError.UNKNOWN_LAYOUT), vm.uiState.value)
    }

    @Test
    fun reviewConfirmsChoosesOrIgnores() = runTest {
        val repo = FakeImportRepository()
        repo.pending.value = listOf(
            PendingPayment(1, LocalDate.of(2025, 3, 5), Money.ofDram(8_000), "ՊՆ 30573 զորամաս", "", 7, "Ministry"),
            PendingPayment(2, LocalDate.of(2025, 3, 6), Money.ofDram(5_000), "Karapetyan", "", null, null),
            PendingPayment(3, LocalDate.of(2025, 3, 7), Money.ofDram(1_000), "Somebody", "", null, null),
        )
        val clients = object : ClientsRepository {
            val list = MutableStateFlow(listOf(ClientSummary(Client(9, "Gamma LLC", ClientType.FIRM, null, "", emptyList(), emptyList()))))
            override fun observeClientSummaries() = list
            override fun observeClientSummary(id: Long) = list.map { l -> l.firstOrNull { it.client.id == id } }
            override suspend fun saveClient(draft: ClientDraft) = SaveClientResult.Saved(0)
            override suspend fun deleteClient(id: Long) = DeleteClientResult.DELETED
        }
        val vm = ReviewViewModel(repo, clients)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onConfirm(vm.uiState.value.payments[0])
        vm.onChooseClient(vm.uiState.value.payments[0])
        vm.onPickQueryChange("gamma")
        assertEquals(1, vm.uiState.value.pickClients.size)
        vm.onClientPicked(9)
        vm.onIgnore(vm.uiState.value.payments.single())
        assertEquals(listOf(1L to 7L, 2L to 9L), repo.assigned)
        assertEquals(listOf(3L), repo.ignored)
        assertTrue(vm.uiState.value.payments.isEmpty())
    }

    @Test
    fun historyUndoAsksFirst() = runTest {
        val repo = FakeImportRepository()
        val batch = ImportBatch(4, ImportKind.INVOICES, "invoice.xlsx", LocalDateTime.of(2026, 10, 9, 10, 0), 365, 365, 0)
        repo.batches.value = listOf(batch)
        val vm = HistoryViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        vm.onUndoClick(batch)
        assertTrue(repo.undone.isEmpty())
        vm.onConfirmUndo()
        assertEquals(listOf(4L), repo.undone)
        assertTrue(vm.uiState.value.batches.isEmpty())
    }
}
