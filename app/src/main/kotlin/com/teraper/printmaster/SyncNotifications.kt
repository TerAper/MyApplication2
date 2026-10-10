package com.teraper.printmaster

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.teraper.printmaster.core.model.SyncReport

/** "Armen finished 2 orders" on the company's phone, "3 new orders" on a master's. */
internal object SyncNotifications {

    private const val CHANNEL = "shared-orders"
    private const val ID = 4100

    fun show(context: Context, report: SyncReport) {
        val lines = buildList {
            if (report.finishedOrders > 0) add(context.resources.getQuantityString(R.plurals.notify_orders_finished, report.finishedOrders, report.finishedOrders))
            if (report.newOrders > 0) add(context.resources.getQuantityString(R.plurals.notify_orders_new, report.newOrders, report.newOrders))
            if (report.newClients > 0) add(context.resources.getQuantityString(R.plurals.notify_clients_new, report.newClients, report.newClients))
        }
        if (lines.isEmpty()) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(lines.first())
            .setContentText(lines.drop(1).joinToString(" · ").ifEmpty { context.getString(R.string.notify_open) })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }
}
