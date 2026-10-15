package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.nearby.LocateResult
import com.teraper.printmaster.core.data.nearby.NearbyRepository
import com.teraper.printmaster.core.model.AddressPoint
import com.teraper.printmaster.core.model.MapPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [addresses] as stored; [locate] answers [result] and remembers where it was asked from. */
class FakeNearbyRepository(addresses: List<AddressPoint> = emptyList()) : NearbyRepository {
    val addresses = MutableStateFlow(addresses)
    var result = LocateResult.DONE
    val locatedNear = mutableListOf<MapPoint?>()

    override fun observeAddresses(): Flow<List<AddressPoint>> = addresses

    override suspend fun locate(near: MapPoint?, onProgress: (Int, Int) -> Unit): LocateResult {
        locatedNear += near
        onProgress(1, 1)
        return result
    }
}
