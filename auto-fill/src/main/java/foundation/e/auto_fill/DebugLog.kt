package foundation.e.auto_fill

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

internal fun Context.isDebugBuild(): Boolean {
    return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

internal inline fun Context.debugLog(tag: String, message: () -> String) {
    if (isDebugBuild()) {
        Log.d(tag, message())
    }
}

internal inline fun Context.debugInfo(tag: String, message: () -> String) {
    if (isDebugBuild()) {
        Log.i(tag, message())
    }
}

internal inline fun Context.debugWarn(
    tag: String,
    error: Throwable? = null,
    message: () -> String
) {
    if (isDebugBuild()) {
        if (error == null) {
            Log.w(tag, message())
        } else {
            Log.w(tag, message(), error)
        }
    }
}

internal inline fun Context.debugError(
    tag: String,
    error: Throwable? = null,
    message: () -> String
) {
    if (isDebugBuild()) {
        if (error == null) {
            Log.e(tag, message())
        } else {
            Log.e(tag, message(), error)
        }
    }
}
