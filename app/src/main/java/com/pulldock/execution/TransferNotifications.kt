package com.pulldock.execution

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pulldock.R
import com.pulldock.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ongoing transfer notifications with pause/stop controls (T111, FR-032). When the
 * notification permission is denied (API 33+), the app still functions — in-app
 * controls remain authoritative; we simply do not post (no crash, no nag loop).
 */
@Singleton
class TransferNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun buildOngoing(title: String, text: String): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_STOP,
            TransferService.stopIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_TRANSFER)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.transfer_notification_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /** Posts only when the user granted notifications (API 33+); silent no-op otherwise. */
    fun post(id: Int, notification: Notification) {
        if (!permissionGranted()) return
        notificationManager().notify(id, notification)
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_TRANSFER,
            context.getString(R.string.transfer_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun permissionGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_TRANSFER = "transfer"
        const val TRANSFER_NOTIFICATION_ID = 4301
        private const val REQUEST_STOP = 4302
        private const val REQUEST_CONTENT = 4303
    }
}
