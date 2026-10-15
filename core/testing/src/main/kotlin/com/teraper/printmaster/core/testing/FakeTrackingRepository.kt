package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.tracking.TrackingRepository
import com.teraper.printmaster.core.model.MapPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In memory: [ways] by order id, [sharing] the orders this phone shares on. */
class FakeTrackingRepository : TrackingRepository {
    val sharing = MutableStateFlow<Set<Long>>(emptySet())
    val ways = MutableStateFlow<Map<Long, List<MapPoint>>>(emptyMap())
    val sent = mutableListOf<MapPoint>()

    override fun observeSharing(): Flow<List<Long>> = sharing.map { it.toList() }

    override suspend fun setSharing(orderId: Long, share: Boolean): Boolean {
        sharing.value = if (share) sharing.value + orderId else sharing.value - orderId
        return true
    }

    override suspend fun send(point: MapPoint) {
        sent += point
    }

    override suspend fun clear(orderIds: Collection<Long>) {
        ways.value = ways.value - orderIds.toSet()
    }

    override fun observeWay(orderId: Long): Flow<List<MapPoint>> = ways.map { it[orderId].orEmpty() }
}
