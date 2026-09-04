package foundation.e.findmydevice.util

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import foundation.e.findmydevice.activity.FindMyDeviceActivity.Companion.TAG
import android.provider.Settings
import android.os.Build
import java.security.MessageDigest

fun hasSimSupport(context: Context): Boolean {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return when (telephonyManager.simState) {
        TelephonyManager.SIM_STATE_READY -> {
            Log.d(TAG, "SIM OK")
            true
        }
        TelephonyManager.SIM_STATE_ABSENT -> {
            Log.d(TAG, "SIM Not found")
            false
        }
        else -> {
            Log.d(TAG, "Invalid SIM State: ${telephonyManager.simState}")
            false
        }
    }
}

fun hasTelephony(context: Context): Boolean {
    val packageManager = context.packageManager
    return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
}

fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() }
        ?: Build.MODEL

fun deviceCredentialKey(context: Context): String? {
    @Suppress("HardwareIds")
    val androidId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )?.takeIf { it.isNotBlank() } ?: return null

    return MessageDigest.getInstance("SHA-256")
        .digest(androidId.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(CREDENTIAL_KEY_LENGTH)
}

private const val CREDENTIAL_KEY_LENGTH = 32
