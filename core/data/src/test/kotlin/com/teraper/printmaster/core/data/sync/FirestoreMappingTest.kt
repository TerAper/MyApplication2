package com.teraper.printmaster.core.data.sync

import com.teraper.printmaster.core.data.sync.FirestoreMapping.toMap
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.SharedClient
import com.teraper.printmaster.core.model.SharedContact
import com.teraper.printmaster.core.model.SharedLine
import com.teraper.printmaster.core.model.SharedOrder
import com.teraper.printmaster.core.model.SharedPriceItem
import com.teraper.printmaster.core.model.SharedItem
import com.teraper.printmaster.core.model.SharedPart
import com.teraper.printmaster.core.model.SharedField
import com.teraper.printmaster.core.model.FieldKind
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
            items = listOf(
                SharedItem(
                    "Printer", "M125", mapOf("Brand" to "HP", "Print" to "Laser"), listOf("Brand"),
                    listOf(SharedPart("Cartridge", "CF283A")), "Office", id = "i1", serialNumber = "SN1",
                    fieldDefs = listOf(SharedField("Brand", inTitle = true), SharedField("Print", FieldKind.CHOICE, listOf("Laser", "Inkjet"))),
                ),
                SharedItem(
                    "Child", "David", mapOf("Birth" to "2019-03-12"), perClient = true, hasSerial = false, id = "i2",
                    parts = listOf(SharedPart("Shoe", "", mapOf("Size" to "30"), fieldDefs = listOf(SharedField("Size", FieldKind.NUMBER)))),
                ),
            ),
            createdByMaster = true,
            visibleTo = listOf("u1"),
        )
        assertEquals(client, FirestoreMapping.client("c1", client.toMap()))

        val order = SharedOrder(
            "o1", "c1", "u1", 1_790_000_000_000, "Refill", "Komitas 5", "091 111111", OrderStatus.DONE, 1_790_000_100_000, true,
            listOf(SharedRepair("r1", "CF283A · HP M125", "", listOf(SharedLine("p1", "Refill", 300_000, 2), SharedLine(null, "Chip", 250_000, 1)))),
        )
        assertEquals(order, FirestoreMapping.order("o1", order.toMap()))

        val price = SharedPriceItem("p1", "Cartridge", "Refill", "85A", 300_000)
        assertEquals(price, FirestoreMapping.priceItem("p1", price.toMap()))
    }
}
