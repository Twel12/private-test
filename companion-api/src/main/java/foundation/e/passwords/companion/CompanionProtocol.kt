package foundation.e.passwords.companion

object CompanionProtocol {
    const val VERSION = 1
    const val PASSWORDS_PACKAGE = "foundation.e.passwords"
    const val SERVICE_ACTION = "foundation.e.passwords.companion.SERVICE"
    const val PERMISSION = "foundation.e.passwords.permission.COMPANION_API"
    const val EXTRA_RESULT = "foundation.e.passwords.companion.extra.RESULT"

    internal const val KEY_VERSION = "version"
    internal const val KEY_STATUS = "status"
    internal const val KEY_ID = "id"
    internal const val KEY_SECRET = "secret"
    internal const val KEY_MODE = "mode"
    internal const val KEY_LABEL = "label"
    internal const val KEY_USERNAME = "username"
    internal const val KEY_NOTES = "notes"
    internal const val KEY_EXTRA_FIELDS = "extraFields"
    internal const val KEY_CREATED_AT = "createdAt"
    internal const val KEY_CREATED = "created"
    internal const val KEY_INTENT = "intent"
    internal const val KEY_ACTION = "action"
    internal const val KEY_REASON = "reason"
    internal const val KEY_CODE = "code"
    internal const val KEY_RETRYABLE = "retryable"
}
