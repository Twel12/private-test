package com.hegocre.nextcloudpasswords.companion

import com.hegocre.nextcloudpasswords.api.session.SessionFailure
import com.hegocre.nextcloudpasswords.api.session.SessionResult
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.UserAction
import foundation.e.passwords.companion.UserReason
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGateTest {

    @Test
    fun `every session failure maps to the documented result`() {
        val expected = mapOf(
            SessionFailure.MasterPasswordMissing to
                GateResult.Blocked(UserAction.UNLOCK_ON_DEVICE, UserReason.VAULT_LOCKED),
            SessionFailure.MasterPasswordRejected to
                GateResult.Blocked(UserAction.UNLOCK_ON_DEVICE, UserReason.MASTER_PASSWORD_CHANGED),
            SessionFailure.ClientDeauthorized to
                GateResult.Blocked(UserAction.ACTION_ON_WEB, UserReason.CLIENT_DEAUTHORISED),
            SessionFailure.AccountUnauthorized to
                GateResult.Blocked(UserAction.SIGN_IN, UserReason.REAUTHENTICATE),
            SessionFailure.SsoReauthenticationRequired to
                GateResult.Blocked(UserAction.SIGN_IN, UserReason.REAUTHENTICATE),
            SessionFailure.NoAccount to
                GateResult.Blocked(UserAction.SIGN_IN, UserReason.NO_ACCOUNT),
            SessionFailure.AppPasswordRequired to
                GateResult.Blocked(UserAction.SIGN_IN, UserReason.APP_PASSWORD_REQUIRED),
            SessionFailure.Transport(null) to GateResult.Failed(FailureCode.NETWORK),
        )

        expected.forEach { (failure, result) -> assertEquals(failure.toString(), result, failure.toGateResult()) }
    }

    @Test
    fun `session without an E2EE key is blocked as not set up`() {
        val result = SessionResult.Success<GateBox<String>?>(null).toGateResult {}

        assertEquals(GateResult.Blocked(UserAction.ACTION_ON_WEB, UserReason.E2EE_NOT_SET_UP), result)
    }

    @Test
    fun `session with an E2EE key opens with the block value`() {
        val result = SessionResult.Success<GateBox<String>?>(GateBox("v")).toGateResult {}

        assertEquals(GateResult.Open("v"), result)
    }

    @Test
    fun `session failure maps and clears the master password only when rejected`() {
        var cleared = 0
        val rejected = failure<String>(SessionFailure.MasterPasswordRejected).toGateResult { cleared++ }
        val missing = failure<String>(SessionFailure.MasterPasswordMissing).toGateResult { cleared++ }

        assertEquals(GateResult.Blocked(UserAction.UNLOCK_ON_DEVICE, UserReason.MASTER_PASSWORD_CHANGED), rejected)
        assertEquals(GateResult.Blocked(UserAction.UNLOCK_ON_DEVICE, UserReason.VAULT_LOCKED), missing)
        assertEquals(1, cleared)
    }

    @Test
    fun `disabled account sync is a blocker that asks to enable sync`() {
        assertEquals(
            GateResult.Blocked(UserAction.ENABLE_SYNC, UserReason.ACCOUNT_SYNC_DISABLED),
            syncDisabledBlocker()
        )
    }

    private fun <T> failure(reason: SessionFailure): SessionResult<GateBox<T>?> = SessionResult.Failure(reason)
}
