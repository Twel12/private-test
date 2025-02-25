package foundation.e.geolocationsms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import foundation.e.geolocationsms.GeolocationSmsActivity

/**
 * This component is responsible for displaying a specified UI element within the application.
 **/
class  UiReceiver :  BroadcastReceiver() {

    companion object {
        private const val TAG = "UiReceiver"
        const val UI_ACTION_KEY = "ACTION_KEY"
        const val UI_ACTION_NEW_PASSWORD = "foundation.e.geolocationsms.NEW_PASSWORD"
        const val UI_ACTION_CHECK_PASSWORD = "foundation.e.geolocationsms.CHECK_PASSWORD"
        const val UI_ACTION_STATUS = "foundation.e.geolocationsms.STATUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "UiReceiver.onReceive() called")

        if (intent.action == UI_ACTION_NEW_PASSWORD) {
            val activityIntent = Intent(context, GeolocationSmsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(UI_ACTION_KEY, intent.action)
            }

            try {
                context.startActivity(activityIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting GeolocationSmsActivity", e)
            }
        }
    }
}