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

import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.utils.AppPasswordRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private fun ApiController.sessionEnvironment() = SessionEnvironment(
    ssoReauthenticationRequired = isSsoReauthenticationRequired(),
    appPasswordRequired = AppPasswordRequest.isBlockingRequests(),
)

@Suppress("TooGenericExceptionCaught")
suspend fun <T> ApiController.withTemporarySession(
    masterPassword: String?,
    clearStoredKeychainOnClose: Boolean = true,
    onCloseFailed: () -> Unit = {},
    block: suspend () -> T,
): SessionResult<T> {
    val opened = try {
        openTemporarySession(masterPassword, clearStoredKeychainOnClose)
    } catch (exception: Exception) {
        return SessionResult.Failure(
            SessionFailureClassifier.classify(exception, sessionEnvironment())
        )
    }

    return when (opened) {
        ApiController.TemporarySessionResult.AlreadyOpen -> SessionResult.Success(block())

        is ApiController.TemporarySessionResult.Opened -> try {
            SessionResult.Success(block())
        } finally {
            // Closing hits the network, which a plain finally would skip on cancellation.
            withContext(NonCancellable) {
                if (!opened.lease.close()) onCloseFailed()
            }
        }

        ApiController.TemporarySessionResult.Failed ->
            SessionResult.Failure(SessionFailureClassifier.classify(null, sessionEnvironment()))
    }
}

/** Opens a session the caller keeps. Returns null on success, otherwise why it could not open. */
@Suppress("TooGenericExceptionCaught")
suspend fun ApiController.openSessionOrFailure(masterPassword: String?): SessionFailure? = try {
    if (openSession(masterPassword)) {
        null
    } else {
        SessionFailureClassifier.classify(null, sessionEnvironment())
    }
} catch (exception: Exception) {
    SessionFailureClassifier.classify(exception, sessionEnvironment())
}
