package foundation.e.geolocationsms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.location.LocationService

/**
 * This component is responsible for receiving and processing incoming SMS messages.
 **/
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val result = manageInMessage(context, messages)
            if (result) {
                processMessages(context, messages)
            }
        }
    }

    private fun processMessages(context: Context, messages: Array<SmsMessage>) {
        val senders = mutableListOf<String>()

        val password = PersistentStorage(context).getPassword()
        if (password == null) {
            Log.e(TAG, "Password is null")
            return
        }

        for (message in messages) {
            val sender = message.originatingAddress
            val body = message.messageBody

            //Log.d(TAG, "SMS received from: $sender")

            if (sender == null || body == null) {
                continue
            }

            if (body.contains(password, ignoreCase = true)) {
                Log.d(TAG, "SMS received from: $sender with password.")
                senders.add(sender)
            }
        }
        executeLocationWorkOnce(senders.toTypedArray(), context)
    }

    private fun executeLocationWorkOnce(sender: Array<String>, context: Context) {
        val serviceIntent = Intent(context, LocationService::class.java)

        serviceIntent.putStringArrayListExtra(LocationService.KEY_SENDER, ArrayList(sender.asList()))
        context.startForegroundService(serviceIntent)
    }

    /**
     * This method is responsible for checking if the incoming messages can be proceed.
     * @Unit testable
     **/
    fun manageInMessage(context: Context, messages: Array<SmsMessage>): Boolean {
        val persistentStorage = PersistentStorage(context)
        val savedStatus = persistentStorage.getStatus()
        if (!savedStatus) {
            Log.d(TAG, "Not enabled.")
            return false
        }
        var found = false
        for (message in messages) {
            val sender = message.originatingAddress
            val body = message.messageBody

            Log.d(TAG, "SMS received from: $sender")
            Log.d(TAG, "Message body: $body")

            if (sender != null  && body != null) {
                found = true
            } else {
                Log.e(TAG, "Sender address is null")
            }
        }
        return found
    }
}
