package com.hegocre.nextcloudpasswords.companion

import com.hegocre.nextcloudpasswords.api.session.SessionFailure
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

fun e2eeNotSetUpBlocker(): GateResult.Blocked =
    GateResult.Blocked(UserAction.ACTION_ON_WEB, UserReason.E2EE_NOT_SET_UP)

fun syncDisabledBlocker(): GateResult.Blocked =
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
