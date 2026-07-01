package foundation.e.findmydevice.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import foundation.e.findmydevice.location.LocationService
import foundation.e.findmydevice.storage.PersistentStorage

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
                val subscriptionId = intent.getIntExtra(
                    "subscription",
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                )
                processMessages(context, messages, subscriptionId)
            }
        }
    }

    private fun processMessages(
        context: Context,
        messages: Array<SmsMessage>,
        subscriptionId: Int
    ) {
        val senders = mutableListOf<String>()

        val password = PersistentStorage(context).getPassword()
        if (password == null) {
            Log.e(TAG, "Password is null")
            return
        }

        for (message in messages) {
            val sender = message.originatingAddress
            val body = message.messageBody

            if (sender == null || body == null) {
                continue
            }

            if (body.equals(password, ignoreCase = true)) {
                Log.d(TAG, "SMS received from: $sender with password.")
                if (senders.indexOf(sender) == -1) {
                    senders.add(sender)
                }
            }
        }
        executeLocationWorkOnce(senders.toTypedArray(), context, subscriptionId)
    }

    private fun executeLocationWorkOnce(
        senders: Array<String>,
        context: Context,
        subscriptionId: Int
    ) {
        if (senders.isEmpty()) {
            Log.d(TAG, "No sender (with password) found.")
            return
        }
        val serviceIntent = Intent(context, LocationService::class.java)

        serviceIntent.putStringArrayListExtra(LocationService.KEY_SENDER, ArrayList(senders.asList()))
        serviceIntent.putExtra(LocationService.KEY_SUB_ID, subscriptionId)
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

            if (sender != null  && body != null) {
                found = true
            } else {
                Log.e(TAG, "Sender address is null")
            }
        }
        return found
    }
}
