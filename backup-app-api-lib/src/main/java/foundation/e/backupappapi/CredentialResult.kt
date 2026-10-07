/*
 * Copyright (C) 2026 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package foundation.e.backupappapi

import android.app.PendingIntent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CredentialResult(
    val status: Int,
    val secret: String? = null,
    val userAction: PendingIntent? = null,
    val retryable: Boolean = false,
) : Parcelable {

    override fun toString(): String =
        "CredentialResult(status=$status, secret=${if (secret == null) "null" else "<redacted>"}, " +
            "userAction=$userAction, retryable=$retryable)"

    companion object {
        const val OK = 0
        const val NOT_FOUND = 1
        const val NEEDS_USER = 2
        const val ERROR = 3

        fun ok(secret: String) = CredentialResult(OK, secret = secret)
        fun notFound() = CredentialResult(NOT_FOUND)
        fun needsUser(userAction: PendingIntent) = CredentialResult(NEEDS_USER, userAction = userAction)
        fun error(retryable: Boolean) = CredentialResult(ERROR, retryable = retryable)
    }
}
