package foundation.e.findmydevice.receiver

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import foundation.e.findmydevice.activity.FindMyDeviceActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

/**
 * UiReceiverTest
 *
 * This class contains unit tests for the UiReceiver class, verifying its behavior when processing
 * intents with specific actions.
 **/
@RunWith(RobolectricTestRunner::class)
class UiReceiverTest {

    private val mockContext = mock(Context::class.java)

    @Test
    fun `onReceive - should start FindMyDeviceActivity with correct intent`() {
        // GIVEN
        val receiver = UiReceiver()
        val broadcastIntent = Intent(UiReceiver.UI_ACTION_NEW_PASSWORD)

        // WHEN
        receiver.onReceive(mockContext, broadcastIntent)

        // THEN : we verify intent sent to startActivity
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(mockContext).startActivity(intentCaptor.capture())
        val capturedIntent = intentCaptor.value

        // Verify intent target class
        val expectedClass = FindMyDeviceActivity::class.java.name
        assert(capturedIntent.component?.className == expectedClass)

        // Verify intent flags
        assert((capturedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assert((capturedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0)

        // Verify the action extra
        assert(capturedIntent.getStringExtra(UiReceiver.UI_ACTION_KEY) == UiReceiver.UI_ACTION_NEW_PASSWORD)
    }

    @Test
    fun `onReceive - catches ActivityNotFoundException`() {
        // GIVEN
        val receiver = UiReceiver()
        val intent = Intent(UiReceiver.UI_ACTION_CHECK_PASSWORD)

        // Simulate exception thrown by context during startActivity
        doThrow(ActivityNotFoundException()).`when`(mockContext).startActivity(any())

        // WHEN no exception should propagate
        receiver.onReceive(mockContext, intent)

        // THEN verify startActivity was called once
        verify(mockContext).startActivity(any())
        // No further action required, exception should be caught internally
    }

    @Test
    fun `onReceive - catches SecurityException`() {
        // GIVEN
        val receiver = UiReceiver()
        val intent = Intent(UiReceiver.UI_ACTION_CHECK_PASSWORD)

        // Simulate exception thrown by context during startActivity
        doThrow(SecurityException()).`when`(mockContext).startActivity(any())

        // WHEN no exception should propagate
        receiver.onReceive(mockContext, intent)

        // THEN verify startActivity was called once
        verify(mockContext).startActivity(any())
    }

    @Test
    fun `onReceive - catches IllegalStateException`() {
        // GIVEN
        val receiver = UiReceiver()
        val intent = Intent(UiReceiver.UI_ACTION_CHECK_PASSWORD)

        // Simulate exception thrown by context during startActivity
        doThrow(IllegalStateException()).`when`(mockContext).startActivity(any())

        // WHEN no exception should propagate
        receiver.onReceive(mockContext, intent)

        // THEN verify startActivity was called once
        verify(mockContext).startActivity(any())
    }
}
