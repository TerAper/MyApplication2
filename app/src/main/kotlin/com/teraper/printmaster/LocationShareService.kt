package com.teraper.printmaster

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.teraper.printmaster.core.data.AppNames
import com.teraper.printmaster.core.data.tracking.TrackingRepository
import com.teraper.printmaster.core.designsystem.component.hasLocationPermission
import com.teraper.printmaster.core.model.MapPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Master's phone: while he shares his location on at least one open order, sends where he is about
 * every minute (or when he moves), with a notification that says so. It stops by itself when no
 * order is shared any more, and the ways of orders that ended or were stopped are deleted.
 */
@AndroidEntryPoint
class LocationShareService : Service() {

    @Inject lateinit var tracking: TrackingRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listening = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch { tracking.send(MapPoint(location.latitude, location.longitude, location.time)) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotice()
        var shared = emptySet<Long>()
        scope.launch {
            tracking.observeSharing().collect { ids ->
                val now = ids.toSet()
                // Stopped or ended since: its way is deleted.
                (shared - now).takeIf { it.isNotEmpty() }?.let { tracking.clear(it) }
                shared = now
                if (now.isEmpty()) stopSelf() else startListening()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startForegroundNotice() {
        val context = AppNames.wrap(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.location_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.location_sharing_title))
            .setContentText(context.getString(R.string.location_sharing_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (listening || !hasLocationPermission()) {
            if (!hasLocationPermission()) stopSelf()
            return
        }
        listening = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_METERS)
            .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
            .build()
        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "location-sharing"
        private const val ID = 4200
        private const val INTERVAL_MS = 60_000L
        private const val MIN_METERS = 25f

        /** Starts sharing when allowed; does nothing without location permission or from the background. */
        fun start(context: Context) {
            if (!context.hasLocationPermission()) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, LocationShareService::class.java))
            } catch (_: Exception) {
                // Android doesn't let a location service start from the background; it starts next time the app opens.
            }
        }
    }
}
