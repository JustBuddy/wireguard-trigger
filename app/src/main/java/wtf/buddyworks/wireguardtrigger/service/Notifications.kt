package wtf.buddyworks.wireguardtrigger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import wtf.buddyworks.wireguardtrigger.MainActivity
import wtf.buddyworks.wireguardtrigger.R

/**
 * The notification Android requires for a foreground service. The app never asks for the
 * notification permission, so on Android 13+ it does not show in the shade at all; the
 * service only appears in the system's list of active apps. The channel is the quietest one
 * possible in case the user allows notifications anyway.
 */
object Notifications {
    private const val CHANNEL_SERVICE = "service"
    const val ID_SERVICE = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun service(context: Context): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(context.getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}
