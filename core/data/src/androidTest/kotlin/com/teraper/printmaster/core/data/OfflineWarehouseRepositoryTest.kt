package com.teraper.printmaster.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.CatalogWriter
import com.teraper.printmaster.core.data.repository.DeleteResult
import com.teraper.printmaster.core.data.repository.OfflineWarehouseRepository
import com.teraper.printmaster.core.data.repository.SaveItemResult
import com.teraper.printmaster.core.data.repository.SaveStockResult
import com.teraper.printmaster.core.model.AdjustReason
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.StockDraft
import com.teraper.printmaster.core.model.StockDraftError
import com.teraper.printmaster.core.model.StockKind
import com.teraper.printmaster.core.model.WarehouseMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class OfflineWarehouseRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repos: TestRepos
    private lateinit var warehouse: OfflineWarehouseRepository
    private var main = 0L
    private var toner = 0L

    @Before
    fun setUp() = runTest {
        context.getSharedPreferences("warehouse", Context.MODE_PRIVATE).edit().clear().commit()
        repos = TestRepos()
        main = repos.register()
        val (_, cartridge) = repos.printerTypes()
        toner = (repos.catalog.saveItem(ItemDraft(typeId = cartridge, name = "CF283A").withKeep("4")) as SaveItemResult.Saved).itemId
        warehouse = newWarehouse()
    }

    private fun newWarehouse() = OfflineWarehouseRepository(
        context, repos.db, repos.db.stockDao(), repos.db.catalogDao(), CatalogWriter(repos.db.catalogDao()), repos.companies, Clock.systemUTC(),
    )

    @After
    fun tearDown() {
        repos.db.close()
        context.getSharedPreferences("warehouse", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private suspend fun save(draft: StockDraft) = warehouse.save(draft)

    @Test
    fun receiveAdjustAndHistory() = runTest {
        assertEquals(SaveStockResult.Saved, save(StockDraft(toner, StockKind.RECEIVE).withQuantity("5").withCost("7000")))
        assertEquals(5, warehouse.observeCount(toner).first())
        // What was paid last is the item's cost now.
        assertEquals(Money.ofDram(7_000), repos.catalog.observeItem(toner).first()!!.cost)

        assertEquals(
            SaveStockResult.Invalid(setOf(StockDraftError.NO_CHANGE)),
            save(StockDraft(toner, StockKind.ADJUST).withQuantity("5")),
        )
        assertEquals(SaveStockResult.Saved, save(StockDraft(toner, StockKind.ADJUST, reason = AdjustReason.BROKEN).withQuantity("2")))

        val line = warehouse.observeStock().first().single { it.item.id == toner }
        assertEquals(2, line.count)
        assertEquals(4, line.keepAtLeast)
        assertTrue(line.isLow)
        assertEquals(listOf(-3, 5), warehouse.observeMoves(toner).first().map { it.quantity })
        assertEquals(AdjustReason.BROKEN, warehouse.observeMoves(toner).first().first().reason)

        // An item with stock history stays: its counts must be explainable.
        assertEquals(DeleteResult.IN_USE, repos.catalog.deleteItem(toner))
    }

    @Test
    fun separateWarehousesSplitMoveAndMerge() = runTest {
        val second = repos.addCompany("Second")
        save(StockDraft(toner, StockKind.RECEIVE).withQuantity("6"))

        // Splitting gives what was shared to the company being viewed.
        warehouse.setMode(WarehouseMode.PER_COMPANY)
        assertEquals(6, warehouse.observeCount(toner).first())
        assertEquals(
            SaveStockResult.Invalid(setOf(StockDraftError.NOT_ENOUGH)),
            save(StockDraft(toner, StockKind.MOVE, toCompanyId = second).withQuantity("7")),
        )
        assertEquals(SaveStockResult.Saved, save(StockDraft(toner, StockKind.MOVE, toCompanyId = second).withQuantity("2")))
        assertEquals(4, warehouse.observeCount(toner).first())

        repos.companies.selectCompany(second)
        assertEquals(2, warehouse.observeCount(toner).first())
        assertEquals(listOf(main), warehouse.observeMoves(toner).first().map { it.otherCompanyId })
        repos.companies.selectCompany(main)

        // The mode is remembered.
        assertEquals(WarehouseMode.PER_COMPANY, newWarehouse().observeMode().first())

        // Back to one warehouse: all pieces together again.
        warehouse.setMode(WarehouseMode.SHARED)
        assertEquals(6, warehouse.observeCount(toner).first())
    }
}
