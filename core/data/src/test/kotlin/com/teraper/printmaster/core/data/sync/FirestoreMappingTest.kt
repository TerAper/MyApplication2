package com.teraper.printmaster.core.data.sync

import com.teraper.printmaster.core.data.sync.FirestoreMapping.toMap
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.core.model.SharedClient
import com.teraper.printmaster.core.model.SharedContact
import com.teraper.printmaster.core.model.SharedLine
import com.teraper.printmaster.core.model.SharedOrder
import com.teraper.printmaster.core.model.SharedPriceItem
import com.teraper.printmaster.core.model.SharedPrinter
import com.teraper.printmaster.core.model.SharedRepair
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreMappingTest {

    @Test
    fun everythingSurvivesTheRoundTrip() {
        val client = SharedClient(
            "c1", "«Ալֆա» ՍՊԸ", ClientType.FIRM, "01234567",
            phones = listOf(SharedContact("091 111111", "accountant")),
            addresses = listOf(SharedContact("Komitas 5", "", "geo:40.1,44.5?q=40.1,44.5")),
            printers = listOf(SharedPrinter("HP", "M125", PrintType.LASER, ColorType.MONO, listOf("CF283A"), "Office")),
            createdByMaster = true,
            visibleTo = listOf("u1"),
        )
        assertEquals(client, FirestoreMapping.client("c1", client.toMap()))

        val order = SharedOrder(
            "o1", "c1", "u1", 1_790_000_000_000, "Refill", "Komitas 5", "091 111111", OrderStatus.DONE, 1_790_000_100_000, true,
            listOf(SharedRepair("r1", "CF283A · HP M125", "", listOf(SharedLine("p1", "Refill", 300_000, 2), SharedLine(null, "Chip", 250_000, 1)))),
        )
        assertEquals(order, FirestoreMapping.order("o1", order.toMap()))

        val price = SharedPriceItem("p1", RepairCategory.CARTRIDGE, "Refill", "85A", 300_000)
        assertEquals(price, FirestoreMapping.priceItem("p1", price.toMap()))
    }
}
