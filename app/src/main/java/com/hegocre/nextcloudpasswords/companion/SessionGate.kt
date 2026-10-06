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
package com.hegocre.nextcloudpasswords.companion

import com.hegocre.nextcloudpasswords.api.session.SessionFailure
import com.hegocre.nextcloudpasswords.api.session.SessionResult
import com.hegocre.nextcloudpasswords.api.session.invalidatesStoredMasterPassword
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.UserAction
import foundation.e.passwords.companion.UserReason

interface SessionGate {
    /** Runs [block] with an open session and an unlocked E2EE key, or says what blocks it. */
    suspend fun <T> withSession(block: suspend () -> T): GateResult<T>
}

sealed interface GateResult<out T> {
    data class Open<out T>(val value: T) : GateResult<T>
    data class Blocked(val action: String, val reason: String) : GateResult<Nothing>
    data class Failed(val code: String) : GateResult<Nothing>
}

internal fun e2eeNotSetUpBlocker(): GateResult.Blocked =
    GateResult.Blocked(UserAction.ACTION_ON_WEB, UserReason.E2EE_NOT_SET_UP)

internal fun syncDisabledBlocker(): GateResult.Blocked =
    GateResult.Blocked(UserAction.ENABLE_SYNC, UserReason.ACCOUNT_SYNC_DISABLED)

fun SessionFailure.toGateResult(): GateResult<Nothing> = when (this) {
    SessionFailure.MasterPasswordMissing ->
        GateResult.Blocked(UserAction.UNLOCK_ON_DEVICE, UserReason.VAULT_LOCKED)
    SessionFailure.MasterPasswordRejected ->
        GateResult.Blocked(UserAction.UNLOCK_ON_DEVICE, UserReason.MASTER_PASSWORD_CHANGED)
    SessionFailure.ClientDeauthorized ->
        GateResult.Blocked(UserAction.ACTION_ON_WEB, UserReason.CLIENT_DEAUTHORISED)
    SessionFailure.AccountUnauthorized,
    SessionFailure.SsoReauthenticationRequired ->
        GateResult.Blocked(UserAction.SIGN_IN, UserReason.REAUTHENTICATE)
    SessionFailure.NoAccount ->
        GateResult.Blocked(UserAction.SIGN_IN, UserReason.NO_ACCOUNT)
    SessionFailure.AppPasswordRequired ->
        GateResult.Blocked(UserAction.SIGN_IN, UserReason.APP_PASSWORD_REQUIRED)
    is SessionFailure.Transport -> GateResult.Failed(FailureCode.NETWORK)
}

internal class GateBox<T>(val value: T)

/** A null success means the session opened but no E2EE key was available. */
internal inline fun <T> SessionResult<GateBox<T>?>.toGateResult(
    onMasterPasswordInvalidated: () -> Unit,
): GateResult<T> = when (this) {
    is SessionResult.Success -> value?.let { GateResult.Open(it.value) } ?: e2eeNotSetUpBlocker()
    is SessionResult.Failure -> {
        if (reason.invalidatesStoredMasterPassword) onMasterPasswordInvalidated()
        reason.toGateResult()
    }
}
