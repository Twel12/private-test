package foundation.e.data

object SetupConsent {
    const val EXTRA_SETUP_RESPONSE = "extra_setup_response"
    const val SETUP_ACTION = "foundation.e.passwords.ACTION_SETUP_E2EE"
}

enum class SetupResponse {
    Success,
    Failed,
    AccountUnavailable,
    E2eeNeedsConfiguration
}
