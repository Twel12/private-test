/*
 *  Copyright MURENA SAS 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.hegocre.nextcloudpasswords.api.session

sealed interface SessionFailure {
    data object MasterPasswordMissing : SessionFailure
    data object MasterPasswordRejected : SessionFailure
    data object AccountUnauthorized : SessionFailure
    data object ClientDeauthorized : SessionFailure
    data object SsoReauthenticationRequired : SessionFailure
    data object AppPasswordRequired : SessionFailure
    data object NoAccount : SessionFailure
    data class Transport(val cause: Throwable?) : SessionFailure
}

data class SessionEnvironment(
    val ssoReauthenticationRequired: Boolean,
    val appPasswordRequired: Boolean,
)

val SessionFailure.invalidatesStoredMasterPassword: Boolean
    get() = when (this) {
        SessionFailure.MasterPasswordRejected -> true

        SessionFailure.MasterPasswordMissing,
        SessionFailure.AccountUnauthorized,
        SessionFailure.ClientDeauthorized,
        SessionFailure.SsoReauthenticationRequired,
        SessionFailure.AppPasswordRequired,
        SessionFailure.NoAccount,
        is SessionFailure.Transport -> false
    }
