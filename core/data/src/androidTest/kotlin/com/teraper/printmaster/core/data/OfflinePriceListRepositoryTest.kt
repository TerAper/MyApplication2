package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.SavePriceItemResult
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceItemError
import com.teraper.printmaster.core.data.repository.SaveTypeResult
import com.teraper.printmaster.core.model.ItemTypeDraft
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

    private suspend fun type(name: String) = (repos.catalog.saveType(ItemTypeDraft(name = name)) as SaveTypeResult.Saved).typeId

    private suspend fun save(name: String, typeId: Long? = null, price: String = "3000", id: Long = 0) =
        priceList.saveItem(PriceItemDraft(id, typeId, name, priceDigits = price, costDigits = "1000"))

    @Test
    fun itemsAreSortedByNameAndKeepTheirType() = runTest {
        val printer = type("Printer")
        save("Թմբուկ", printer)
        save("Չիպ")
        save("Ամբողջական լիցք")

        val items = priceList.observeItems().first()
        assertEquals(listOf("Ամբողջական լիցք", "Թմբուկ", "Չիպ"), items.map { it.name })
        assertEquals(listOf(null, printer, null), items.map { it.typeId })
        assertEquals(Money.ofDram(2_000), items.first().profit)
    }

    @Test
    fun sameNameForTheSameTypeIsRejected() = runTest {
        val cartridge = type("Cartridge")
        val id = (save("Refill 85A", cartridge) as SavePriceItemResult.Saved).itemId
        assertEquals(SavePriceItemResult.NameTaken, save("  refill   85a ", cartridge))
        assertTrue(save("Refill 85A") is SavePriceItemResult.Saved)
        // Saving the item itself under the same name is fine.
        assertTrue(save("Refill 85A", cartridge, price = "3500", id = id) is SavePriceItemResult.Saved)
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
