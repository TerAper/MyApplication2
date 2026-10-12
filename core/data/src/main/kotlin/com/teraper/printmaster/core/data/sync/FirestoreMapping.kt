package com.teraper.printmaster.core.data.sync

import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.SharedClient
import com.teraper.printmaster.core.model.SharedContact
import com.teraper.printmaster.core.model.SharedLine
import com.teraper.printmaster.core.model.SharedOrder
import com.teraper.printmaster.core.model.SharedPriceItem
import com.teraper.printmaster.core.model.SharedItem
import com.teraper.printmaster.core.model.SharedPart
import com.teraper.printmaster.core.model.SharedRepair

/** Shared values ↔ Firestore documents (plain maps; enums by name, money in minor units). */
internal object FirestoreMapping {

    fun SharedClient.toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "type" to type.name,
        "taxId" to taxId,
        "phones" to phones.map { it.toMap() },
        "addresses" to addresses.map { it.toMap() },
        "items" to items.map {
            mapOf(
                "type" to it.type, "name" to it.name, "fields" to it.fields, "titleFields" to it.titleFields,
                "parts" to it.parts.map { p -> mapOf("type" to p.type, "name" to p.name) },
                "place" to it.place, "id" to it.id, "serial" to it.serialNumber,
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
        items = d.list("items").map {
            SharedItem(
                type = it.string("type"),
                name = it.string("name"),
                fields = (it["fields"] as? Map<*, *>).orEmpty().entries
                    .mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap(),
                titleFields = (it["titleFields"] as? List<*>).orEmpty().filterIsInstance<String>(),
                parts = it.list("parts").map { p -> SharedPart(p.string("type"), p.string("name")) },
                place = it.string("place"),
                id = it.string("id"),
                serialNumber = it.string("serial"),
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
                "id" to r.id, "device" to r.device, "note" to r.note, "itemId" to r.itemId,
                "lines" to r.lines.map { mapOf("itemId" to it.itemId, "name" to it.name, "priceMinor" to it.priceMinor, "quantity" to it.quantity) },
            )
        },
        "createdByMaster" to createdByMaster,
        "takenByMaster" to takenByMaster,
        "declinedReason" to declinedReason,
        "handedOver" to handedOver,
        "accepted" to accepted,
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
                itemId = r["itemId"] as? String,
            )
        },
        createdByMaster = d["createdByMaster"] == true,
        takenByMaster = d["takenByMaster"] == true,
        declinedReason = d["declinedReason"] as? String,
        handedOver = d["handedOver"] == true,
        accepted = d["accepted"] == true,
    )

    fun SharedPriceItem.toMap(): Map<String, Any?> = mapOf(
        "type" to type, "name" to name, "description" to description, "priceMinor" to priceMinor, "archived" to archived,
    )

    fun priceItem(id: String, d: Map<String, Any?>) = SharedPriceItem(
        id = id,
        type = d["type"] as? String,
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
