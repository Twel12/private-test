package foundation.e.findmydevice.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object PasswordsVault {

    private const val PASSWORDS_PACKAGE = "foundation.e.passwords"
    private const val SAVE_ACTIVITY =
        "com.hegocre.nextcloudpasswords.ui.activities.SaveFindMyDeviceCodeActivity"

    private const val ACTION_SAVE_CREDENTIAL = "foundation.e.passwords.ACTION_SAVE_CREDENTIAL"
    private const val EXTRA_PASSWORD = "foundation.e.passwords.extra.PASSWORD"
    private const val EXTRA_USERNAME = "foundation.e.passwords.extra.USERNAME"
    private const val EXTRA_IDENTITY_KEY = "foundation.e.passwords.extra.IDENTITY_KEY"

    fun saveCodeIntent(context: Context, code: String): Intent =
        Intent(ACTION_SAVE_CREDENTIAL).apply {
            component = ComponentName(PASSWORDS_PACKAGE, SAVE_ACTIVITY)
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(EXTRA_USERNAME, deviceName(context))
            putExtra(EXTRA_IDENTITY_KEY, deviceCredentialKey(context))
            putExtra(EXTRA_PASSWORD, code)
        }
}
