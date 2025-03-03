package foundation.e.geolocationsms.receiver

import android.content.Context
import android.telephony.SmsMessage
import androidx.test.core.app.ApplicationProvider
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.util.SmsSender
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

/**
 * SmsReceiverTest
 *
 * This class contains unit tests for the SmsReceiver class, verifying its behavior when processing
 * incoming SMS messages.
 **/

@RunWith(RobolectricTestRunner::class)
class SmsReceiverTest {

    private lateinit var smsReceiver: SmsReceiver
    private lateinit var context: Context
    private lateinit var persistentStorage: PersistentStorage
    private lateinit var smsSender: SmsSender

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        persistentStorage = PersistentStorage(context)
        smsSender = SmsSender(context)
        smsReceiver = SmsReceiver()
    }

    @Test
    fun `onReceive should process SMS when enabled`() {
        val message = arrayOf(createMockSmsMessage())
        persistentStorage.saveStatus(true)
        val result = smsReceiver.manageInMessage(context, message)
        assertTrue(result)
    }

    @Test
    fun `onReceive should not process SMS when disabled`() {
        val message = arrayOf(createMockSmsMessage())
        persistentStorage.saveStatus(false)
        val result = smsReceiver.manageInMessage(context, message)
        assertFalse(result)
    }

    private fun createMockSmsMessage(): SmsMessage {
        val sender = "+33611223344"
        val body = "Test message"
        val smsMessage = Mockito.mock(SmsMessage::class.java)
        Mockito.`when`(smsMessage.originatingAddress).thenReturn(sender)
        Mockito.`when`(smsMessage.messageBody).thenReturn(body)
        return smsMessage
    }

}
