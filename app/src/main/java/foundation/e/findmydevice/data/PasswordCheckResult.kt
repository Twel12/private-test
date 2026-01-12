package foundation.e.findmydevice.data

import kotlinx.serialization.Serializable

@Serializable
data class PasswordCheckResult(
    val first: Long,
    val second: Boolean
)
