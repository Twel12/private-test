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
package com.hegocre.nextcloudpasswords.services.autofill

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import foundation.e.autofill.CredentialGetActivity
import foundation.e.autofill.PasswordEntry
import foundation.e.autofill.PasswordQuery
import foundation.e.autofill.PasswordRequestSource
import foundation.e.autofill.toCredentialManagerRequestContext
import kotlinx.coroutines.launch
import timber.log.Timber

class NCPCredentialUnlockActivity : FragmentActivity() {
    private val passwordsViewModel by viewModels<PasswordsViewModel>()
    private var completed = false
    private var beginGetRequest: BeginGetCredentialRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        beginGetRequest = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
        if (beginGetRequest == null) {
            Timber.d("missing BeginGetCredentialRequest")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        if (!UserController.getInstance(this).isLoggedIn) {
            Timber.d("user is not logged in")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            NCPAppLockWrapper {
                NextcloudPasswordsApp(
                    passwordsViewModel = passwordsViewModel,
                    onLogOut = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    isAutofillUnlockRequest = true,
                    onAutofillUnlockComplete = ::completeUnlock
                )
            }
        }
    }

    private fun completeUnlock() {
        if (completed) return
        completed = true

        val request = beginGetRequest
        if (request == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            val result = Intent()
            runCatching {
                val response = buildUnlockedResponse(request)
                if (response.credentialEntries.isEmpty()) {
                    PendingIntentHandler.setGetCredentialException(
                        result,
                        GetCredentialUnknownException("No matching passwords")
                    )
                } else {
                    PendingIntentHandler.setBeginGetCredentialResponse(result, response)
                }
                setResult(RESULT_OK, result)
            }.getOrElse { error ->
                Timber.e(error,"failed to complete credential unlock")
                setResult(RESULT_CANCELED)
            }
            finish()
        }
    }

    private suspend fun buildUnlockedResponse(
        request: BeginGetCredentialRequest
    ): BeginGetCredentialResponse {
        val passwordOptions = request.beginGetCredentialOptions
            .filterIsInstance<BeginGetPasswordOption>()

        if (passwordOptions.isEmpty()) {
            return BeginGetCredentialResponse()
        }

        val credentialContext = request.callingAppInfo.toCredentialManagerRequestContext(
            NCPCredentialManagerPrivilegedApps.json(this)
        )
        val queryResult = NCPApplication.passwordBackend(this).query(
            PasswordQuery(
                source = PasswordRequestSource.CREDENTIAL_MANAGER,
                packageName = credentialContext.packageName,
                webDomain = credentialContext.webDomain,
                origin = credentialContext.origin,
                usernameHint = null,
                hasPasswordField = true,
                isWebOriginRequest = credentialContext.isWebOriginRequest
            )
        )
        Timber.d(
            "unlocked query package=${credentialContext.packageName}, " +
                "webDomain=${credentialContext.webDomain}, origin=${credentialContext.origin}, " +
                "credentials=${queryResult.credentials.size}, vaultLocked=${queryResult.vaultLocked}"
        )

        val credentialEntries = passwordOptions.flatMap { option ->
            queryResult.credentials
                .filter { credential -> credential.isAllowedFor(option) }
                .map { credential ->
                    PasswordCredentialEntry(
                        context = applicationContext,
                        username = credential.username,
                        pendingIntent = createPasswordGetPendingIntent(credential.id),
                        beginGetPasswordOption = option,
                        displayName = credential.displayName,
                        icon = Icon.createWithResource(this, R.mipmap.ic_launcher),
                        isAutoSelectAllowed = !credential.locked && credential.password != null,
                        affiliatedDomain = credentialContext.affiliatedDomain
                    )
                }
        }

        return BeginGetCredentialResponse(credentialEntries = credentialEntries)
    }

    private fun PasswordEntry.isAllowedFor(option: BeginGetPasswordOption): Boolean {
        return username.isNotBlank() &&
            (option.allowedUserIds.isEmpty() ||
                username in option.allowedUserIds ||
                id in option.allowedUserIds)
    }

    private fun createPasswordGetPendingIntent(credentialId: String): PendingIntent {
        val intent = Intent(this, NCPCredentialGetActivity::class.java)
            .setIdentifier(credentialId)
            .putExtra(CredentialGetActivity.EXTRA_CREDENTIAL_ID, credentialId)

        return PendingIntent.getActivity(
            this,
            PASSWORD_GET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val PASSWORD_GET_REQUEST_CODE = 28043
    }
}
