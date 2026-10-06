package foundation.e.passwords.companion

import android.app.PendingIntent

sealed interface CompanionResult
sealed interface GetResult : CompanionResult
sealed interface SaveResult : CompanionResult
sealed interface DeleteResult : CompanionResult
sealed interface CommonResult : GetResult, SaveResult, DeleteResult

data class Found(val secret: String, val createdAt: Long) : GetResult {
    override fun toString() = "Found(createdAt=$createdAt)"
}

data object NotFound : GetResult, DeleteResult

data class Saved(val created: Boolean) : SaveResult

data object AlreadyExists : SaveResult

data object Deleted : DeleteResult

data class NeedsUser(val intent: PendingIntent, val action: String, val reason: String) : CommonResult

data class Failed(
    val code: String,
    val retryable: Boolean = FailureCode.isRetryable(code),
) : CommonResult
