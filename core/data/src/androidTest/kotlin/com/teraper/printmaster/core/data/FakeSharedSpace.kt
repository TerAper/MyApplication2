package com.teraper.printmaster.core.data

import com.teraper.printmaster.core.data.sync.SyncBackend
import com.teraper.printmaster.core.model.SharedChanges
import com.teraper.printmaster.core.model.SharedClient
import com.teraper.printmaster.core.model.SharedOrder
import com.teraper.printmaster.core.model.SharedPriceItem

/**
 * An in-memory shared space with the same visibility rules the real one will have:
 * the owner sees everything, a master only his orders, their clients, his own clients and prices.
 */
class FakeSharedSpace {
    private data class Doc<T>(val value: T, val seq: Long, val author: String)

    private var seq = 0L
    private val clients = HashMap<String, Doc<SharedClient>>()
    private val orders = HashMap<String, Doc<SharedOrder>>()
    private val prices = HashMap<String, Doc<SharedPriceItem>>()

    fun orders(): List<SharedOrder> = orders.values.map { it.value }

    fun member(uid: String, isOwner: Boolean = false): SyncBackend = object : SyncBackend {
        override val myUid: String = uid

        override suspend fun push(changes: SharedChanges) {
            changes.clients.forEach { clients[it.id] = Doc(it, ++seq, uid) }
            changes.orders.forEach { orders[it.id] = Doc(it, ++seq, uid) }
            changes.priceItems.forEach { prices[it.id] = Doc(it, ++seq, uid) }
        }

        override suspend fun pull(cursor: String?): Pair<SharedChanges, String?> {
            val since = cursor?.toLong() ?: 0
            fun <T> List<Doc<T>>.fresh() = filter { it.seq > since && it.author != uid }.map { it.value }
            val myOrders = orders.values.filter { isOwner || it.value.masterUid == uid }
            val visibleClientIds = myOrders.map { it.value.clientId }.toSet()
            val myClients = clients.values.filter { isOwner || it.value.id in visibleClientIds || it.author == uid }
            return SharedChanges(myClients.fresh(), myOrders.fresh(), prices.values.toList().fresh()) to seq.toString()
        }
    }
}
