package foundation.e.findmydevice.util

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import foundation.e.passwords.companion.DeleteResult
import foundation.e.passwords.companion.EntryPresentation
import foundation.e.passwords.companion.GetResult
import foundation.e.passwords.companion.PasswordsCompanion
import foundation.e.passwords.companion.SaveMode
import foundation.e.passwords.companion.SaveResult
import foundation.e.passwords.companion.Saved
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PasswordsMirrorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the code is saved under the device key, replacing the old one`() = runBlocking {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "abc123")
        val passwords = RecordingPasswords()

        assertEquals(Saved(created = false), PasswordsMirror.save(context, passwords, "CODE1234"))

        val call = passwords.calls.single()
        assertEquals("6ca13d52ca70c883e0f0bb101e425a89", call.first)
        assertEquals("CODE1234", call.second)
        assertEquals(SaveMode.REPLACE, call.third)
        assertEquals("Find my Device", call.fourth.label)
        assertEquals(deviceName(context), call.fourth.username)
        assertEquals("Managed by Find My Device. Don't edit.", call.fourth.notes)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private class RecordingPasswords : PasswordsCompanion {
        val calls = mutableListOf<Quad<String, String, SaveMode, EntryPresentation>>()

        override suspend fun get(id: String): GetResult = error("not used")

        override suspend fun save(
            id: String,
            secret: String,
            mode: SaveMode,
            presentation: EntryPresentation
        ): SaveResult {
            calls += Quad(id, secret, mode, presentation)
            return Saved(created = false)
        }

        override suspend fun delete(id: String): DeleteResult = error("not used")
    }
}
