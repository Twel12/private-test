package foundation.e.geolocationsms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val SENT = "SMS_SENT"
        private const val DELIVERED = "SMS_DELIVERED"
    }

    fun sendSms(phoneNumber: String, message: String) {

        val sentPI = PendingIntent.getBroadcast(context, 0, Intent(SENT), PendingIntent.FLAG_IMMUTABLE)
        val deliveredPI = PendingIntent.getBroadcast(context, 0, Intent(DELIVERED), PendingIntent.FLAG_IMMUTABLE)

        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(arg0: Context, arg1: Intent) {
                Log.d(TAG, "SMS sent")
            }
        }, IntentFilter(SENT), ContextCompat.RECEIVER_NOT_EXPORTED)

        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(arg0: Context, arg1: Intent) {
                Log.d(TAG, "SMS delivered")
            }
        }, IntentFilter(DELIVERED), ContextCompat.RECEIVER_NOT_EXPORTED)

        val smsManager = context.getSystemService(SmsManager::class.java)
        smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
    }
}
