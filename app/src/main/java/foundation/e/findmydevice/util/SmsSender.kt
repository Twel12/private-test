package foundation.e.findmydevice.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import foundation.e.findmydevice.R
import java.util.ArrayList


/**
 * SmsSender
 *
 * This class is responsible for sending SMS messages from the application.
 **/
class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val DELIVERED = "SMS_DELIVERED"
    }

    fun sendSms(phoneNumber: String, latitude: Double?, longitude: Double?) {
        val message: String = if (latitude != null && longitude != null) {
            context.getString(R.string.sms_message_with_location) + "\n" +
                    context.getString(R.string.sms_message_with_location_1, latitude.toString(), longitude.toString()) + "\n" +
                    context.getString(R.string.sms_message_with_location_2, latitude.toString(), longitude.toString())
        } else {
            context.getString(R.string.sms_message_with_location_not_found)
        }

        sendSmsDirect(phoneNumber, message)
    }

    fun sendSmsDirect(phoneNumber: String, message: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val sentPI = PendingIntent.getBroadcast(
                context,
                phoneNumber.hashCode(),
                Intent().apply {},
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val deliveredPI = PendingIntent.getBroadcast(
                context,
                phoneNumber.hashCode(),
                Intent(DELIVERED),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val parts: List<String> = smsManager.divideMessage(message)

            if (parts.size > 1) {
                val sentList = List(parts.size) { sentPI }
                val deliveredList = List(parts.size) { deliveredPI }
                smsManager.sendMultipartTextMessage(phoneNumber, null,
                    parts as ArrayList<String>?,
                    sentList as ArrayList<PendingIntent>?,
                    deliveredList as ArrayList<PendingIntent>?
                )
            } else {
                Log.d(TAG, "Try to sent SMS to $phoneNumber")
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to send SMS", e)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot send SMS : ${e.message}", e)
            throw e // This one should retry
        }
    }
}
