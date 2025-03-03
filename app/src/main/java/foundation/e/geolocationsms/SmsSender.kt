package foundation.e.geolocationsms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

/**
 * SmsSender
 *
 * This class is responsible for sending SMS messages from the application.
 **/
class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val SENT = "SMS_SENT"
        private const val DELIVERED = "SMS_DELIVERED"
    }

    fun sendSms(phoneNumber: String, message: String) {
        try {
            val sentPI = PendingIntent.getBroadcast(
                context, 0, Intent(SENT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            val deliveredPI = PendingIntent.getBroadcast(
                context, 0, Intent(DELIVERED),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )

            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception sending SMS: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Illegal argument exception sending SMS: ${e.message}", e)
        }
    }
}
