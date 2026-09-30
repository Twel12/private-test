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

import com.hegocre.nextcloudpasswords.api.exceptions.ClientDeauthorizedException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.api.exceptions.SsoReauthenticationRequiredException
import com.hegocre.nextcloudpasswords.api.exceptions.UnauthorizedException
import com.hegocre.nextcloudpasswords.api.exceptions.twoFactorErrorCodeOrNull
import com.hegocre.nextcloudpasswords.data.user.UserException
import kotlinx.coroutines.CancellationException

object SessionFailureClassifier {

    /** [throwable] is null when the attempt failed without throwing. Rethrows cancellation. */
    fun classify(throwable: Throwable?, environment: SessionEnvironment): SessionFailure {
        if (throwable is CancellationException) throw throwable
        return if (throwable == null) {
            classifyState(environment)
        } else {
            classifyThrowable(throwable)
        }
    }

    // Only read when nothing was thrown: the SSO flag stays set until explicitly cleared.
    private fun classifyState(environment: SessionEnvironment): SessionFailure = when {
        environment.ssoReauthenticationRequired -> SessionFailure.SsoReauthenticationRequired
        environment.appPasswordRequired -> SessionFailure.AppPasswordRequired
        else -> SessionFailure.Transport(null)
    }

    private fun classifyThrowable(throwable: Throwable): SessionFailure = when (throwable) {
        is PWDv1ChallengeMasterKeyNeededException -> SessionFailure.MasterPasswordMissing
        is PWDv1ChallengeMasterKeyInvalidException -> SessionFailure.MasterPasswordRejected
        is PWDv1ChallengePasswordException -> SessionFailure.MasterPasswordRejected
        is UnauthorizedException -> SessionFailure.AccountUnauthorized
        is ClientDeauthorizedException -> SessionFailure.ClientDeauthorized
        is SsoReauthenticationRequiredException -> SessionFailure.SsoReauthenticationRequired
        is UserException -> SessionFailure.NoAccount
        else -> classifyUnrecognised(throwable)
    }

    private fun classifyUnrecognised(throwable: Throwable): SessionFailure {
        return if (throwable.twoFactorErrorCodeOrNull() != null) {
            SessionFailure.AppPasswordRequired
        } else {
            SessionFailure.Transport(throwable)
        }
    }
}
