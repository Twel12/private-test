package foundation.e.geolocationsms.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import foundation.e.geolocationsms.R

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
        private const val MAX_SMS_LENGTH = 160
    }

    fun sendSms(phoneNumber: String, latitude: Double?, longitude: Double?) {
        try {
            Log.d(TAG, "Sending SMS to $phoneNumber")
            val sentPI = PendingIntent.getBroadcast(
                context, 0, Intent(SENT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            val deliveredPI = PendingIntent.getBroadcast(
                context, 0, Intent(DELIVERED),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )

            val smsManager = context.getSystemService(SmsManager::class.java)
            var message: String
            if (latitude != null && longitude != null) {
                message = context.getString(R.string.sms_message_with_location) + "\n"
                message += context.getString(R.string.sms_message_with_location_1, latitude.toString(), longitude
                    .toString()) + "\n"
                message += context.getString(R.string.sms_message_with_location_2, latitude.toString(), longitude
                    .toString())
            } else {
                message = context.getString(R.string.sms_message_with_location_not_found)
            }
            Log.d(TAG, "Sending SMS: $message")

            if (message.length > MAX_SMS_LENGTH) {
                Log.w(TAG, "SMS message length exceeds 160 characters. Dividing message.")
                sendLongSms(phoneNumber, message, sentPI, deliveredPI)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception sending SMS: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Illegal argument exception sending SMS: ${e.message}", e)
        }
    }

    private fun sendLongSms(phoneNumber: String, message: String, sentPI:PendingIntent, deliveredPI:PendingIntent) {
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(message)
        Log.d(TAG, "Sending long SMS with ${parts.size} parts")
        val sentIntents = ArrayList<PendingIntent>()
        val deliveredIntents = ArrayList<PendingIntent>()

        parts.forEach { _ ->
            sentIntents.add(sentPI)
            deliveredIntents.add(deliveredPI)
        }
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents)
    }
}
