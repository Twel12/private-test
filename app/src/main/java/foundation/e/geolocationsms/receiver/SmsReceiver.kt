package foundation.e.geolocationsms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import foundation.e.geolocationsms.PersistentStorage
import foundation.e.geolocationsms.SmsSender

/**
 * This component is responsible for receiving and processing incoming SMS messages.
 **/
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {

            val persistentStorage = PersistentStorage(context)
            val savedStatus = persistentStorage.getStatus()
            if (!savedStatus) {
                Log.d(TAG, "Not enabled.")
                return
            }

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
