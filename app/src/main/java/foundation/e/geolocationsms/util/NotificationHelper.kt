package foundation.e.geolocationsms.util


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.activity.GeolocationSmsActivity

/**
 * This component handles the display of a low-priority notification to the user.
 **/
class NotificationHelper {

/*
val notificationIntent = Intent(this, NotificationReceiver::class.java)
this.sendBroadcast(notificationIntent)
 */
    companion object {
        const val CHANNEL_ID = "geosms_notification_channel"
    }

    fun createNotificationChannel(context: Context) {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun createNotification(context: Context): Notification {
        val notificationIntent = Intent(context, GeolocationSmsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_servcie_caption))
            .setContentText(context.getString(R.string.notification_service_content))
            .setSmallIcon(R.drawable.ic_launcher_foreground) //K1ZFP TODO
            .setContentIntent(pendingIntent)
            .build()
        return notification
    }
}
