package foundation.e.findmydevice.util

import android.content.Context
import foundation.e.passwords.companion.EntryPresentation
import foundation.e.passwords.companion.Failed
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.PasswordsCompanion
import foundation.e.passwords.companion.SaveMode
import foundation.e.passwords.companion.SaveResult

object PasswordsMirror {
    private const val LABEL = "Find my Device"
    private const val NOTES = "Managed by Find My Device. Don't edit."

    suspend fun save(context: Context, passwords: PasswordsCompanion, code: String): SaveResult {
        val id = deviceCredentialKey(context) ?: return Failed(FailureCode.INVALID_ID)
        return passwords.save(id, code, SaveMode.REPLACE, EntryPresentation(LABEL, deviceName(context), NOTES))
    }
}
