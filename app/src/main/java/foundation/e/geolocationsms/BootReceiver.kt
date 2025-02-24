package foundation.e.geolocationsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest

class BootReceiver : BroadcastReceiver() {

    companion object {
        private var isInit = false;
        fun init(context: Context) {
            if (!isInit) {
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
    override fun doWork(): Result {
        registerSmsReceiver(applicationContext)
        return Result.success()
    }

    private fun registerSmsReceiver(context: Context) {
        val smsReceiver = SmsReceiver()
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        context.registerReceiver(smsReceiver, filter, "android.permission.BROADCAST_SMS", null)
    }


}