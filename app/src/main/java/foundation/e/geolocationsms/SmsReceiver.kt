package foundation.e.geolocationsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val sender = message.originatingAddress
                val body = message.messageBody

                Log.d(TAG, "SMS received from: $sender")
                Log.d(TAG, "Message body: $body")

                if (sender != null) {
                    SmsSender(context).sendSms(sender, body)
                } else {
                    Log.e(TAG, "Sender address is null")
                }
            }
        }
    }


}