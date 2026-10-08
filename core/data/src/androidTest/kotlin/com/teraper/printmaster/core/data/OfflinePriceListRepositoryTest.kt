package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.SavePriceItemResult
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceItemError
import com.teraper.printmaster.core.model.RepairCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePriceListRepositoryTest {

    private val repos = TestRepos()
    private val priceList = repos.priceList

    @After
    fun tearDown() = repos.db.close()

    private suspend fun save(name: String, category: RepairCategory = RepairCategory.CARTRIDGE, price: String = "3000", id: Long = 0) =
        priceList.saveItem(PriceItemDraft(id, category, name, priceDigits = price, costDigits = "1000"))

    @Test
    fun itemsAreSortedByCategoryThenName() = runTest {
        save("Թմբուկ", RepairCategory.PRINTER)
        save("Չիպ")
        save("Ամբողջական լիցք")

        val items = priceList.observeItems().first()
        assertEquals(listOf("Ամբողջական լիցք", "Չիպ", "Թմբուկ"), items.map { it.name })
        assertEquals(Money.ofDram(2_000), items.first().profit)
    }

    @Test
    fun sameNameInSameCategoryIsRejected() = runTest {
        val id = (save("Refill 85A") as SavePriceItemResult.Saved).itemId
        assertEquals(SavePriceItemResult.NameTaken, save("  refill   85a "))
        assertTrue(save("Refill 85A", RepairCategory.OTHER) is SavePriceItemResult.Saved)
        // Saving the item itself under the same name is fine.
        assertTrue(save("Refill 85A", price = "3500", id = id) is SavePriceItemResult.Saved)
        assertEquals(Money.ofDram(3_500), priceList.observeItem(id).first()!!.price)
        assertEquals(SavePriceItemResult.Invalid(setOf(PriceItemError.PRICE_REQUIRED)), save("Free", price = ""))
    }

    @Test
    fun deletedItemIsGone() = runTest {
        val id = (save("Chip") as SavePriceItemResult.Saved).itemId
        assertTrue(priceList.deleteItem(id))
        assertFalse(priceList.deleteItem(id))
        assertNull(priceList.observeItem(id).first())
    }
}
