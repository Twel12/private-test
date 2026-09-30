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
package com.hegocre.nextcloudpasswords.backupApp

import com.hegocre.nextcloudpasswords.api.session.SessionFailure
import foundation.e.backupappapi.E2eeKeyWrapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionFailureToE2eeKeyWrapperTest {

    @Test
    fun `master password failures report e2ee unavailable`() {
        assertEquals(
            E2eeKeyWrapper.E2eeUnavailable(),
            SessionFailure.MasterPasswordMissing.toE2eeKeyWrapper(),
        )
        assertEquals(
            E2eeKeyWrapper.E2eeUnavailable(),
            SessionFailure.MasterPasswordRejected.toE2eeKeyWrapper(),
        )
    }

    @Test
    fun `account failures report the account as unavailable`() {
        assertEquals(
            E2eeKeyWrapper.MurenaAccountUnavailable(),
            SessionFailure.AccountUnauthorized.toE2eeKeyWrapper(),
        )
        assertEquals(
            E2eeKeyWrapper.MurenaAccountUnavailable(),
            SessionFailure.ClientDeauthorized.toE2eeKeyWrapper(),
        )
        assertEquals(
            E2eeKeyWrapper.MurenaAccountUnavailable(),
            SessionFailure.NoAccount.toE2eeKeyWrapper(),
        )
    }

    /** A stale SSO token used to fall through to a retryable error, so the client retried forever. */
    @Test
    fun `sso reauthentication is terminal rather than retryable`() {
        assertEquals(
            E2eeKeyWrapper.MurenaAccountUnavailable(),
            SessionFailure.SsoReauthenticationRequired.toE2eeKeyWrapper(),
        )
    }

    @Test
    fun `pending two factor and transport failures are retryable`() {
        assertEquals(
            E2eeKeyWrapper.ApiError(shouldRetry = true),
            SessionFailure.AppPasswordRequired.toE2eeKeyWrapper(),
        )
        assertEquals(
            E2eeKeyWrapper.ApiError(shouldRetry = true),
            SessionFailure.Transport(null).toE2eeKeyWrapper(),
        )
    }
}
