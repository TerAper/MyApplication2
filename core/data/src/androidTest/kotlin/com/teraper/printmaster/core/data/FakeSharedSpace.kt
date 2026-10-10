package com.teraper.printmaster.core.data

import com.teraper.printmaster.core.data.sync.SyncBackend
import com.teraper.printmaster.core.model.SharedChanges
import com.teraper.printmaster.core.model.SharedClient
import com.teraper.printmaster.core.model.SharedMember
import com.teraper.printmaster.core.model.SharedOrder
import com.teraper.printmaster.core.model.SharedPriceItem

/**
 * Firestore in memory: spaces with members and documents, and the same read rules —
 * an owner reads everything of its space, a master only his orders and their clients.
 */
class FakeSharedSpace {
    private data class Doc<T>(val value: T, val seq: Long, val author: String)

    private class Space {
        val clients = HashMap<String, Doc<SharedClient>>()
        val orders = HashMap<String, Doc<SharedOrder>>()
        val prices = HashMap<String, Doc<SharedPriceItem>>()
        val members = LinkedHashMap<String, SharedMember>()
    }

    private var seq = 0L
    private val spaces = HashMap<String, Space>()

    private fun space(id: String) = spaces.getOrPut(id) { Space() }

    fun orders(spaceId: String): List<SharedOrder> = space(spaceId).orders.values.map { it.value }

    /** Someone entered the space's code. */
    fun attach(spaceId: String, uid: String, name: String) {
        space(spaceId).members[uid] = SharedMember(uid, name, "$uid@gmail.com")
    }

    fun phone(uid: String): SyncBackend = object : SyncBackend {
        override val myUid: String = uid

        override suspend fun push(spaceId: String, changes: SharedChanges) {
            val s = space(spaceId)
            changes.clients.forEach { s.clients[it.id] = Doc(it, ++seq, uid) }
            changes.orders.forEach { s.orders[it.id] = Doc(it, ++seq, uid) }
            changes.priceItems.forEach { s.prices[it.id] = Doc(it, ++seq, uid) }
        }

        override suspend fun pull(spaceId: String, asOwner: Boolean, cursor: String?): Pair<SharedChanges, String?> {
            val s = space(spaceId)
            val since = cursor?.toLong() ?: 0
            fun <T> List<Doc<T>>.fresh() = filter { it.seq > since && it.author != uid }.map { it.value }
            val myOrders = s.orders.values.filter { asOwner || it.value.masterUid == uid }
            val myClients = s.clients.values.filter { asOwner || uid in it.value.visibleTo }
            return SharedChanges(myClients.fresh(), myOrders.fresh(), s.prices.values.toList().fresh()) to seq.toString()
        }

        override suspend fun members(spaceId: String): List<SharedMember> = space(spaceId).members.values.toList()
    }
}
