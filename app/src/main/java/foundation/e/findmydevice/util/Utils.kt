package foundation.e.findmydevice.util

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import foundation.e.findmydevice.activity.FindMyDeviceActivity.Companion.TAG

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