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
import com.hegocre.nextcloudpasswords.api.exceptions.HttpStatusException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.api.exceptions.SsoReauthenticationRequiredException
import com.hegocre.nextcloudpasswords.api.exceptions.UnauthorizedException
import com.hegocre.nextcloudpasswords.data.user.UserException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException

class SessionFailureClassifierTest {

    private val noFlags = SessionEnvironment(
        ssoReauthenticationRequired = false,
        appPasswordRequired = false,
    )

    private fun classify(
        throwable: Throwable?,
        environment: SessionEnvironment = noFlags,
    ): SessionFailure = SessionFailureClassifier.classify(throwable, environment)

    @Test
    fun `master key needed is master password missing`() {
        assertEquals(
            SessionFailure.MasterPasswordMissing,
            classify(PWDv1ChallengeMasterKeyNeededException()),
        )
    }

    @Test
    fun `master key invalid is master password rejected`() {
        assertEquals(
            SessionFailure.MasterPasswordRejected,
            classify(PWDv1ChallengeMasterKeyInvalidException()),
        )
    }

    @Test
    fun `password length violation is master password rejected`() {
        assertEquals(
            SessionFailure.MasterPasswordRejected,
            classify(PWDv1ChallengePasswordException("too short")),
        )
    }

    @Test
    fun `unauthorized is account unauthorized`() {
        assertEquals(SessionFailure.AccountUnauthorized, classify(UnauthorizedException()))
    }

    @Test
    fun `client deauthorized is client deauthorized`() {
        assertEquals(SessionFailure.ClientDeauthorized, classify(ClientDeauthorizedException()))
    }

    @Test
    fun `sso exception is sso reauthentication required`() {
        assertEquals(
            SessionFailure.SsoReauthenticationRequired,
            classify(SsoReauthenticationRequiredException(IOException("token gone"))),
        )
    }

    @Test
    fun `no throwable with sso flag is sso reauthentication required`() {
        assertEquals(
            SessionFailure.SsoReauthenticationRequired,
            classify(null, noFlags.copy(ssoReauthenticationRequired = true)),
        )
    }

    @Test
    fun `no throwable with app password flag is app password required`() {
        assertEquals(
            SessionFailure.AppPasswordRequired,
            classify(null, noFlags.copy(appPasswordRequired = true)),
        )
    }

    @Test
    fun `http see other is app password required`() {
        assertEquals(
            SessionFailure.AppPasswordRequired,
            classify(HttpStatusException(HttpURLConnection.HTTP_SEE_OTHER)),
        )
    }

    @Test
    fun `http see other nested in causes is app password required`() {
        val nested = IOException(
            "outer",
            IOException("middle", HttpStatusException(HttpURLConnection.HTTP_SEE_OTHER)),
        )
        assertEquals(SessionFailure.AppPasswordRequired, classify(nested))
    }

    @Test
    fun `user exception is no account`() {
        assertEquals(SessionFailure.NoAccount, classify(UserException("Not logged in")))
    }

    @Test
    fun `other http status is transport`() {
        assertTrue(classify(HttpStatusException(HttpURLConnection.HTTP_INTERNAL_ERROR)) is SessionFailure.Transport)
    }

    @Test
    fun `socket timeout is transport carrying the original cause`() {
        val timeout = SocketTimeoutException("timed out")
        val failure = classify(timeout)
        assertEquals(SessionFailure.Transport(timeout), failure)
        assertSame(timeout, (failure as SessionFailure.Transport).cause)
    }

    @Test
    fun `no throwable and no flags is transport with no cause`() {
        assertEquals(SessionFailure.Transport(null), classify(null))
    }

    @Test
    fun `an exception outranks a stale sso flag`() {
        assertEquals(
            SessionFailure.MasterPasswordRejected,
            classify(
                PWDv1ChallengeMasterKeyInvalidException(),
                noFlags.copy(ssoReauthenticationRequired = true),
            ),
        )
    }

    @Test
    fun `sso flag outranks app password flag`() {
        assertEquals(
            SessionFailure.SsoReauthenticationRequired,
            classify(
                null,
                SessionEnvironment(ssoReauthenticationRequired = true, appPasswordRequired = true),
            ),
        )
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown rather than classified`() {
        classify(CancellationException("cancelled"))
    }

    @Test
    fun `cancellation nested as a cause is not rethrown`() {
        assertTrue(classify(IOException("wrapper", CancellationException("inner"))) is SessionFailure.Transport)
    }

    @Test
    fun `only master password rejected invalidates the stored master password`() {
        assertTrue(SessionFailure.MasterPasswordRejected.invalidatesStoredMasterPassword)

        // Deleting a stored credential is destructive, so the other cases are listed by hand
        // rather than derived from the implementation.
        val keepsStoredPassword = listOf(
            SessionFailure.MasterPasswordMissing,
            SessionFailure.AccountUnauthorized,
            SessionFailure.ClientDeauthorized,
            SessionFailure.SsoReauthenticationRequired,
            SessionFailure.AppPasswordRequired,
            SessionFailure.NoAccount,
            SessionFailure.Transport(null),
        )
        keepsStoredPassword.forEach { failure ->
            assertFalse(
                "invalidatesStoredMasterPassword for $failure",
                failure.invalidatesStoredMasterPassword,
            )
        }
    }
}
