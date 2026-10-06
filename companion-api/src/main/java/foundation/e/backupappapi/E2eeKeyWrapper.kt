package foundation.e.backupappapi


import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

sealed class E2eeKeyWrapper : Parcelable {

    @Parcelize
    data class E2eeKey(
        val id: UUID,
        val data: String,
        val isNewlyCreated: Boolean,
        val createdTimestamp: Long
    ) : E2eeKeyWrapper()

    @Parcelize
    data class MurenaAccountUnavailable(val errorCode: Int = 1) : E2eeKeyWrapper()

    @Parcelize
    data class E2eeUnavailable(val errorCode: Int = 2) : E2eeKeyWrapper()

    @Parcelize
    data class ApiError(val shouldRetry: Boolean) : E2eeKeyWrapper()
}
