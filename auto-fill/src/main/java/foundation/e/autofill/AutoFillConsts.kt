package foundation.e.autofill

object AutoFillConsts {

    val IGNORED_PACKAGE_NAMES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.android.provision",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.setupwizard",
        "foundation.e.setupwizard",
        "foundation.e.settings"
    )
    val IGNORED_FIELD_KEYWORDS = arrayOf(
        "search",
        "otp",
        "one-time",
        "verification"
    )
    val IGNORED_CONTEXT_KEYWORDS = arrayOf(
        "wifi",
        "wi-fi",
        "network",
        "hotspot",
        "settings",
        "pin",
        "passcode",
        "screen lock",
        "device password",
        "sim",
        "vpn"
    )

}