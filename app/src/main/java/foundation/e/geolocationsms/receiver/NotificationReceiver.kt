package foundation.e.geolocationsms.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import foundation.e.geolocationsms.GeolocationSmsActivity
import foundation.e.geolocationsms.R

/**
 * This component handles the display of a low-priority notification to the user.
 **/
class NotificationReceiver : BroadcastReceiver() {

/*
val notificationIntent = Intent(this, NotificationReceiver::class.java)
this.sendBroadcast(notificationIntent)
 */
    companion object {
        const val CHANNEL_ID = "geosms_notification_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "NotificationReceiver"

        private var isNotificationInit = false;
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "NotificationReceiver.onReceive() called")
        if (!isNotificationInit) {
            createNotificationChannel(context)
            isNotificationInit = true
        }
        showNotification(context)
    }

    private fun createNotificationChannel(context: Context) {
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

    private fun showNotification(context: Context) {
        val intent = Intent(context, GeolocationSmsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background) //K1ZFP TODO
            .setContentTitle(context.getString(R.string.notification_caption))
            .setContentText(context.getString(R.string.notification_content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}
