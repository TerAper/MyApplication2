package com.teraper.printmaster.core.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.teraper.printmaster.core.data.sync.FirestoreMapping.toMap
import com.teraper.printmaster.core.data.team.FirebaseTeamRepository
import com.teraper.printmaster.core.model.SharedChanges
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shared space in Firestore. Every document carries its author and a server time;
 * a pull asks for documents newer than the last one seen (minus a margin, re-applying is harmless).
 * Masters only query what the rules let them read: their orders and the clients visible to them.
 */
@Singleton
internal class FirebaseSyncBackend @Inject constructor(private val team: FirebaseTeamRepository) : SyncBackend {

    override val myUid: String? get() = team.uid?.takeIf { team.available && team.currentSpace != null }

    private fun space() = team.firestore.collection("workspaces").document(checkNotNull(team.currentSpace).id)

    override suspend fun push(changes: SharedChanges) {
        val uid = checkNotNull(myUid)
        val writes = changes.clients.map { "clients" to (it.id to it.toMap()) } +
            changes.orders.map { "orders" to (it.id to it.toMap()) } +
            changes.priceItems.map { "prices" to (it.id to it.toMap()) }
        // Firestore takes up to 500 writes per batch.
        writes.chunked(400).forEach { chunk ->
            val batch = team.firestore.batch()
            chunk.forEach { (collection, doc) ->
                val (id, data) = doc
                batch.set(space().collection(collection).document(id), data + mapOf("author" to uid, "updatedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit().await()
        }
    }

    override suspend fun pull(cursor: String?): Pair<SharedChanges, String?> {
        val uid = checkNotNull(myUid)
        val isOwner = team.currentSpace?.isOwner == true
        val since = cursor?.toLongOrNull()?.let { it - MARGIN_MS } ?: 0L
        val newer: (Query) -> Query = { it.whereGreaterThan("updatedAt", Timestamp(Date(since))) }

        val orders = if (isOwner) newer(space().collection("orders")) else space().collection("orders").whereEqualTo("masterUid", uid)
        val clients = if (isOwner) newer(space().collection("clients")) else space().collection("clients").whereArrayContains("visibleTo", uid)
        val prices = newer(space().collection("prices"))

        val docs = listOf(orders, clients, prices).map { it.get().await().documents }
        var latest = cursor?.toLongOrNull() ?: 0L
        fun DocumentSnapshot.fresh(): Boolean {
            val at = getTimestamp("updatedAt")?.toDate()?.time ?: return false
            latest = maxOf(latest, at)
            return at > since && getString("author") != uid
        }
        val changes = SharedChanges(
            clients = docs[1].filter { it.fresh() }.map { FirestoreMapping.client(it.id, it.data.orEmpty()) },
            orders = docs[0].filter { it.fresh() }.map { FirestoreMapping.order(it.id, it.data.orEmpty()) },
            priceItems = docs[2].filter { it.fresh() }.map { FirestoreMapping.priceItem(it.id, it.data.orEmpty()) },
        )
        return changes to latest.toString()
    }

    private companion object {
        /** Writes from other phones can land with a slightly older server time than one already seen. */
        const val MARGIN_MS = 10 * 60 * 1000L
    }
}
