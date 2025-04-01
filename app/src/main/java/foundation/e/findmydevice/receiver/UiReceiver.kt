package foundation.e.findmydevice.receiver

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import foundation.e.findmydevice.activity.FindMyDeviceActivity

/**
 * This component is responsible for displaying a specified UI element within the application.
 **/
class  UiReceiver :  BroadcastReceiver() {

    // REmove

    companion object {
        private const val TAG = "UiReceiver"
        const val UI_ACTION_KEY = "ACTION_KEY"
        const val UI_ACTION_NEW_PASSWORD = "foundation.e.findmydevice.NEW_PASSWORD"
        const val UI_ACTION_CHECK_PASSWORD = "foundation.e.findmydevice.CHECK_PASSWORD"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "UiReceiver.onReceive() called")

        val activityIntent = Intent(context, FindMyDeviceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(UI_ACTION_KEY, intent.action)
        }

        try {
            context.startActivity(activityIntent)
        }  catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Activity not found", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state exception", e)
        }

    }
}
