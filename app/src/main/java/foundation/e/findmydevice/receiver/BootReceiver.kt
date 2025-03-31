package foundation.e.findmydevice.receiver

import android.Manifest.permission.BROADCAST_SMS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest

/**
 * This component handles the initialization of background tasks when the device completes booting.
 **/
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private var isInit = false;
        fun init(context: Context) {
            if (!isInit) {
                Log.d(TAG, "BootReceiver.init() called")
                isInit = true
                val workRequest: WorkRequest = OneTimeWorkRequestBuilder<BackgroundWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            init(context)
        }
    }
}

class BackgroundWorker(appContext: Context, workerParams: androidx.work.WorkerParameters) :
    androidx.work.Worker(appContext, workerParams) {

    companion object {
        private const val TAG = "BackgroundWorker"
    }

    override fun doWork(): Result {
        val smsReceiver = SmsReceiver()
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        try {
            // Be sure the receiver is not registered
            applicationContext.unregisterReceiver(smsReceiver)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Receiver not registered ${e.message}")
        }
        applicationContext.registerReceiver(smsReceiver, filter, BROADCAST_SMS, null)
        return Result.success()
    }

}
