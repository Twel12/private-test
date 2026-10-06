package foundation.e.passwords.companion

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompanionCodecTest {

    private fun intent(): PendingIntent = PendingIntent.getActivity(
        RuntimeEnvironment.getApplication(), 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE
    )

    @Test
    fun `get results round-trip`() {
        assertEquals(Found("s3cret", 42L), CompanionCodec.decodeGet(CompanionCodec.encode(Found("s3cret", 42L))))
        assertEquals(NotFound, CompanionCodec.decodeGet(CompanionCodec.encode(NotFound)))
    }

    @Test
    fun `save and delete results round-trip`() {
        assertEquals(Saved(created = true), CompanionCodec.decodeSave(CompanionCodec.encode(Saved(created = true))))
        assertEquals(AlreadyExists, CompanionCodec.decodeSave(CompanionCodec.encode(AlreadyExists)))
        assertEquals(Deleted, CompanionCodec.decodeDelete(CompanionCodec.encode(Deleted)))
        assertEquals(NotFound, CompanionCodec.decodeDelete(CompanionCodec.encode(NotFound)))
    }

    @Test
    fun `needs user keeps its intent, action and reason`() {
        val needsUser = NeedsUser(intent(), UserAction.ACTION_ON_WEB, UserReason.CLIENT_DEAUTHORISED)
        assertEquals(needsUser, CompanionCodec.decodeSave(CompanionCodec.encode(needsUser)))
    }

    @Test
    fun `failed keeps its code and retryability`() {
        assertEquals(Failed(FailureCode.MODIFIED, false), CompanionCodec.decodeGet(CompanionCodec.encode(Failed(FailureCode.MODIFIED))))
        assertTrue(Failed(FailureCode.NETWORK).retryable)
    }

    @Test
    fun `unknown status becomes failed unknown`() {
        val bundle = Bundle().apply { putString("status", "SOMETHING_NEW") }
        assertEquals(Failed(FailureCode.UNKNOWN, false), CompanionCodec.decodeGet(bundle))
    }

    @Test
    fun `missing result bundle means the user cancelled`() {
        assertEquals(Failed(FailureCode.CANCELED, true), CompanionCodec.decodeSave(null))
    }

    @Test
    fun `save request round-trips`() {
        val request = SaveRequest(
            id = "murena-device-backup:v1",
            secret = "k",
            mode = SaveMode.CREATE_ONLY,
            presentation = EntryPresentation("L", "U", "N", mapOf("foundation.e.backup.key" to "v")),
        )
        assertEquals(request, CompanionCodec.readSaveRequest(CompanionCodec.saveRequest(request)))
        assertEquals("x", CompanionCodec.readId(CompanionCodec.getRequest("x")))
    }

    @Test
    fun `save request with unknown mode is rejected`() {
        val bundle = CompanionCodec.saveRequest(SaveRequest("a", "b", SaveMode.REPLACE, EntryPresentation("", "", "")))
        bundle.putString("mode", "MERGE")
        assertNull(CompanionCodec.readSaveRequest(bundle))
    }

    @Test
    fun `credential ids follow the rule`() {
        assertTrue(CredentialId.isValid("murena-device-backup:v1"))
        assertTrue(CredentialId.isValid("0f3a9c"))
        assertFalse(CredentialId.isValid(""))
        assertFalse(CredentialId.isValid("Upper"))
        assertFalse(CredentialId.isValid("a".repeat(65)))
    }

    @Test
    fun `secrets never appear in toString`() {
        assertFalse(Found("s3cret", 1L).toString().contains("s3cret"))
        assertFalse(SaveRequest("a", "s3cret", SaveMode.REPLACE, EntryPresentation("", "", "")).toString().contains("s3cret"))
    }
}
