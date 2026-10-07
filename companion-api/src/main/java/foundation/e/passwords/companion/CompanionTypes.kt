package foundation.e.passwords.companion

enum class SaveMode { CREATE_ONLY, REPLACE }

data class EntryPresentation(
    val label: String,
    val username: String,
    val notes: String,
    val extraDataFields: Map<String, String> = emptyMap(),
)

data class SaveRequest(
    val id: String,
    val secret: String,
    val mode: SaveMode,
    val presentation: EntryPresentation,
) {
    override fun toString() = "SaveRequest(id=$id, mode=$mode)"
}

object CredentialId {
    private val PATTERN = Regex("^[a-z0-9._:-]{1,64}$")

    fun isValid(id: String): Boolean = PATTERN.matches(id)
}

object UserAction {
    const val UNLOCK_ON_DEVICE = "UNLOCK_ON_DEVICE"
    const val ACTION_ON_WEB = "ACTION_ON_WEB"
    const val SIGN_IN = "SIGN_IN"
    const val ENABLE_SYNC = "ENABLE_SYNC"
    const val CHOOSE_ENTRY = "CHOOSE_ENTRY"
}

object UserReason {
    const val VAULT_LOCKED = "VAULT_LOCKED"
    const val MASTER_PASSWORD_CHANGED = "MASTER_PASSWORD_CHANGED"
    const val CLIENT_DEAUTHORISED = "CLIENT_DEAUTHORISED"
    const val E2EE_NOT_SET_UP = "E2EE_NOT_SET_UP"
    const val NO_ACCOUNT = "NO_ACCOUNT"
    const val REAUTHENTICATE = "REAUTHENTICATE"
    const val APP_PASSWORD_REQUIRED = "APP_PASSWORD_REQUIRED"
    const val ACCOUNT_SYNC_DISABLED = "ACCOUNT_SYNC_DISABLED"
    const val CONFLICT = "CONFLICT"
}

object FailureCode {
    const val NETWORK = "NETWORK"
    const val SERVER = "SERVER"
    const val EXPIRED = "EXPIRED"
    const val CANCELED = "CANCELED"
    const val UNREADABLE = "UNREADABLE"
    const val MODIFIED = "MODIFIED"
    const val NOT_ALLOWED = "NOT_ALLOWED"
    const val INVALID_ID = "INVALID_ID"
    const val UNKNOWN = "UNKNOWN"

    private val RETRYABLE = setOf(NETWORK, SERVER, EXPIRED, CANCELED, UNREADABLE)

    fun isRetryable(code: String): Boolean = code in RETRYABLE
}
