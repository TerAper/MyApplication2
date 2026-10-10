package com.teraper.printmaster.core.data.sync

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

/** Shared values ↔ Firestore documents (plain maps; enums by name, money in minor units). */
internal object FirestoreMapping {

    fun SharedClient.toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "type" to type.name,
        "taxId" to taxId,
        "phones" to phones.map { it.toMap() },
        "addresses" to addresses.map { it.toMap() },
        "printers" to printers.map {
            mapOf(
                "brand" to it.brand, "model" to it.model, "printType" to it.printType.name, "colorType" to it.colorType.name,
                "cartridges" to it.cartridges, "location" to it.location, "id" to it.id,
            )
        },
        "createdByMaster" to createdByMaster,
        "visibleTo" to visibleTo,
    )

    fun client(id: String, d: Map<String, Any?>) = SharedClient(
        id = id,
        name = d.string("name"),
        type = enumOr(d["type"], ClientType.FIRM),
        taxId = d["taxId"] as? String,
        phones = d.list("phones").map(::contact),
        addresses = d.list("addresses").map(::contact),
        printers = d.list("printers").map {
            SharedPrinter(
                brand = it.string("brand"),
                model = it.string("model"),
                printType = enumOr(it["printType"], PrintType.LASER),
                colorType = enumOr(it["colorType"], ColorType.MONO),
                cartridges = (it["cartridges"] as? List<*>).orEmpty().filterIsInstance<String>(),
                location = it.string("location"),
                id = it.string("id"),
            )
        },
        createdByMaster = d["createdByMaster"] == true,
        visibleTo = (d["visibleTo"] as? List<*>).orEmpty().filterIsInstance<String>(),
    )

    fun SharedOrder.toMap(): Map<String, Any?> = mapOf(
        "clientId" to clientId,
        "masterUid" to masterUid,
        "scheduledAt" to scheduledAt,
        "description" to description,
        "address" to address,
        "phone" to phone,
        "status" to status.name,
        "doneAt" to doneAt,
        "paidCash" to paidCash,
        "work" to work.map { r ->
            mapOf(
                "id" to r.id, "device" to r.device, "note" to r.note, "printerId" to r.printerId,
                "lines" to r.lines.map { mapOf("itemId" to it.itemId, "name" to it.name, "priceMinor" to it.priceMinor, "quantity" to it.quantity) },
            )
        },
        "createdByMaster" to createdByMaster,
    )

    fun order(id: String, d: Map<String, Any?>) = SharedOrder(
        id = id,
        clientId = d.string("clientId"),
        masterUid = d["masterUid"] as? String,
        scheduledAt = d.long("scheduledAt") ?: 0,
        description = d.string("description"),
        address = d["address"] as? String,
        phone = d["phone"] as? String,
        status = enumOr(d["status"], OrderStatus.NEW),
        doneAt = d.long("doneAt"),
        paidCash = d["paidCash"] as? Boolean,
        work = d.list("work").map { r ->
            SharedRepair(
                id = r.string("id"),
                device = r["device"] as? String,
                note = r.string("note"),
                lines = r.list("lines").map { SharedLine(it["itemId"] as? String, it.string("name"), it.long("priceMinor") ?: 0, (it.long("quantity") ?: 1).toInt()) },
                printerId = r["printerId"] as? String,
            )
        },
        createdByMaster = d["createdByMaster"] == true,
    )

    fun SharedPriceItem.toMap(): Map<String, Any?> = mapOf(
        "category" to category.name, "name" to name, "description" to description, "priceMinor" to priceMinor, "archived" to archived,
    )

    fun priceItem(id: String, d: Map<String, Any?>) = SharedPriceItem(
        id = id,
        category = enumOr(d["category"], RepairCategory.OTHER),
        name = d.string("name"),
        description = d.string("description"),
        priceMinor = d.long("priceMinor") ?: 0,
        archived = d["archived"] == true,
    )

    private fun SharedContact.toMap() = mapOf("value" to value, "label" to label, "mapLink" to mapLink)

    private fun contact(d: Map<String, Any?>) = SharedContact(d.string("value"), d.string("label"), d["mapLink"] as? String)

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { it as Map<String, Any?> }

    private inline fun <reified T : Enum<T>> enumOr(value: Any?, default: T): T =
        (value as? String)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
}
