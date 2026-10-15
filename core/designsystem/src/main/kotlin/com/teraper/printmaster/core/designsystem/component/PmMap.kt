package com.teraper.printmaster.core.designsystem.component

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.teraper.printmaster.core.model.MapPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** One way on the map: a line through [points], and a pin at the last one titled [title] / [snippet]. */
data class MapWay(val id: Long, val title: String, val snippet: String, val points: List<MapPoint>, val colorIndex: Int = 0, val isMe: Boolean = false)

/**
 * An OpenStreetMap map (free, no key) with people's ways. It fits them all when the set of ways
 * changes, then the user moves and zooms freely; new points don't move the map.
 */
@Composable
fun PmMap(ways: List<MapWay>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val map = remember { context.mapView() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }
    val key = remember(ways.map { it.id }.toSet(), ways.any { it.points.isNotEmpty() }) { Any() }
    // The map draws its tiles past its edges unless clipped.
    AndroidView(factory = { map }, modifier = modifier.clipToBounds()) { view ->
        view.overlays.clear()
        ways.forEach { way ->
            if (way.points.isEmpty()) return@forEach
            val color = CompanyColors.of(way.colorIndex).toArgb()
            val geo = way.points.map { GeoPoint(it.lat, it.lng) }
            if (geo.size > 1) {
                view.overlays += Polyline(view).apply {
                    setPoints(geo)
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 8f
                }
            }
            view.overlays += Marker(view).apply {
                position = geo.last()
                title = way.title
                snippet = way.snippet
                if (way.isMe) {
                    // Where the user stands: a blue dot, not a pin like the places around.
                    icon = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(6, android.graphics.Color.WHITE)
                        setSize(44, 44)
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                } else {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            }
        }
        // Fit everything once per set of ways (the key), not on every new point.
        if (view.tag !== key) {
            view.tag = key
            val all = ways.flatMap { it.points }
            when {
                all.isEmpty() -> Unit
                all.size == 1 || all.all { it.lat == all[0].lat && it.lng == all[0].lng } -> view.post {
                    view.controller.setZoom(16.0)
                    view.controller.setCenter(GeoPoint(all[0].lat, all[0].lng))
                }
                else -> view.post {
                    val box = BoundingBox.fromGeoPoints(all.map { GeoPoint(it.lat, it.lng) })
                    view.zoomToBoundingBox(box.increaseByScale(1.3f), false, 64)
                }
            }
        }
        view.invalidate()
    }
}

private fun Context.mapView(): MapView {
    // OpenStreetMap asks every app to name itself; tiles are cached in the app's own storage.
    Configuration.getInstance().apply {
        userAgentValue = packageName
        osmdroidBasePath = cacheDir.resolve("osmdroid")
        osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
    }
    return MapView(this).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(12.0)
        // Yerevan until there's a point to show.
        controller.setCenter(GeoPoint(40.1792, 44.4991))
    }
}
