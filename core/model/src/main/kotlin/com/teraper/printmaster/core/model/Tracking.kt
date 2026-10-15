package com.teraper.printmaster.core.model

/** Where a master was at [at] (epoch millis), shared while doing an order. */
data class MapPoint(val lat: Double, val lng: Double, val at: Long = 0)

/** A master's way during one order, as the company sees it; the last point is where he is now. */
data class MasterTrack(
    val orderId: Long,
    val masterName: String,
    val clientName: String,
    val points: List<MapPoint>,
) {
    val last: MapPoint? get() = points.lastOrNull()
}
