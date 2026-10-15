package com.teraper.printmaster.core.data.tracking

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.teraper.printmaster.core.data.team.FirebaseTeamRepository
import com.teraper.printmaster.core.database.dao.OrderDao
import com.teraper.printmaster.core.database.dao.TrackRef
import com.teraper.printmaster.core.model.MapPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a master is while doing an order the company asked him to share it on. Only with his
 * yes, only while the order is open; the way is deleted when he stops or the order ends.
 *
 * Firestore: workspaces/{space}/tracks/{order sync id} → { masterUid, points: [{lat, lng, at}] }.
 */
interface TrackingRepository {
    /** Master's phone: local ids of the orders he shares his location on now. */
    fun observeSharing(): Flow<List<Long>>

    /** Master's phone: share (or stop sharing) where he is on [orderId]. False when it can't be shared. */
    suspend fun setSharing(orderId: Long, share: Boolean): Boolean

    /** Master's phone: where he is now, added to the way of every order he shares on. */
    suspend fun send(point: MapPoint)

    /** Master's phone: the ways of [orderIds] are deleted (stopped or the orders ended). */
    suspend fun clear(orderIds: Collection<Long>)

    /** Company's phone: the master's way on [orderId], live; empty when nothing is shared. */
    fun observeWay(orderId: Long): Flow<List<MapPoint>>
}

@Singleton
internal class FirebaseTrackingRepository @Inject constructor(
    private val team: FirebaseTeamRepository,
    private val orderDao: OrderDao,
) : TrackingRepository {

    /** Kept so a way can still be deleted after its order closed and left the list. */
    private val known = HashMap<Long, TrackRef>()

    private fun doc(ref: TrackRef) =
        team.firestore.collection("workspaces").document(ref.spaceId).collection("tracks").document(ref.orderSyncId)

    override fun observeSharing(): Flow<List<Long>> = orderDao.observeLocationShares().map { refs ->
        synchronized(known) { refs.forEach { known[it.orderId] = it } }
        refs.map { it.orderId }
    }.distinctUntilChanged()

    override suspend fun setSharing(orderId: Long, share: Boolean): Boolean {
        val ref = orderDao.getTrackRef(orderId)
        orderDao.setSharingLocation(orderId, share)
        if (!share && ref != null) runCatching { doc(ref).delete().await() }
        return ref != null || !share
    }

    override suspend fun send(point: MapPoint) {
        val uid = team.uid ?: return
        for (ref in orderDao.getLocationShares()) {
            runCatching {
                doc(ref).set(
                    mapOf(
                        "masterUid" to uid,
                        "points" to FieldValue.arrayUnion(mapOf("lat" to point.lat, "lng" to point.lng, "at" to point.at)),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
            }
        }
    }

    override suspend fun clear(orderIds: Collection<Long>) {
        if (team.uid == null) return
        for (id in orderIds) {
            val ref = synchronized(known) { known[id] } ?: orderDao.getTrackRef(id) ?: continue
            runCatching { doc(ref).delete().await() }
        }
    }

    override fun observeWay(orderId: Long): Flow<List<MapPoint>> {
        if (!team.available || team.uid == null) return flowOf(emptyList())
        return callbackFlow {
            val ref = orderDao.getTrackRef(orderId)
            if (ref == null) {
                send(emptyList())
                awaitClose()
                return@callbackFlow
            }
            val listener = doc(ref).addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val points = (snapshot?.get("points") as? List<Map<String, Any?>>).orEmpty().mapNotNull { p ->
                    val lat = (p["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng = (p["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                    MapPoint(lat, lng, (p["at"] as? Number)?.toLong() ?: 0)
                }.sortedBy { it.at }
                trySend(points)
            }
            awaitClose { listener.remove() }
        }
    }
}
