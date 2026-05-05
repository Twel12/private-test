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
package foundation.e.auto_fill

import android.app.PendingIntent
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
abstract class MurenaCredentialProviderService : CredentialProviderService() {
    protected abstract fun passwordBackend(): MurenaPasswordBackend

    protected abstract fun credentialSaveActivityClass(): Class<out Activity>

    protected abstract fun credentialGetActivityClass(): Class<out Activity>

    protected abstract fun credentialUnlockActivityClass(): Class<out Activity>

    protected open fun createEntryAccountName(): String =
        getString(R.string.credential_provider_create_entry_title)

    protected open fun createEntryDescription(): String =
        getString(R.string.credential_provider_create_entry_description)

    protected open fun unlockActionTitle(): String =
        getString(R.string.autofill_unlock_vault)

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        if (request !is BeginCreatePasswordCredentialRequest) {
            callback.onError(CreateCredentialUnknownException("Only password creation is supported"))
            return
        }

        runBackendCall(
            cancellationSignal = cancellationSignal,
            call = {
                passwordBackend().query(
                    PasswordQuery(
                        source = PasswordRequestSource.CREDENTIAL_MANAGER,
                        packageName = request.callingAppInfo?.packageName,
                        webDomain = null,
                        origin = null,
                        usernameHint = null,
                        hasPasswordField = true
                    )
                )
            },
            onSuccess = { queryResult ->
                if (!queryResult.allowSavePrompt) {
                    callback.onError(CreateCredentialUnknownException("Password save is unavailable"))
                    return@runBackendCall
                }

                val createEntry = CreateEntry(
                    accountName = createEntryAccountName(),
                    pendingIntent = createPasswordSavePendingIntent(),
                    icon = Icon.createWithResource(this, R.drawable.ic_autofill_provider),
                    totalCredentialCount = queryResult.savedPasswordCount,
                    passwordCredentialCount = queryResult.savedPasswordCount,
                    description = createEntryDescription()
                )

                callback.onResult(
                    BeginCreateCredentialResponse(
                        createEntries = listOf(createEntry)
                    )
                )
            },
            onError = { error ->
                callback.onError(
                    CreateCredentialUnknownException(
                        error.message ?: "Could not prepare password save"
                    )
                )
            }
        )
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        val passwordOptions = request.beginGetCredentialOptions
            .filterIsInstance<BeginGetPasswordOption>()

        if (passwordOptions.isEmpty()) {
            callback.onError(GetCredentialUnknownException("No password option requested"))
            return
        }

        runBackendCall(
            cancellationSignal = cancellationSignal,
            call = {
                passwordBackend().query(
                    PasswordQuery(
                        source = PasswordRequestSource.CREDENTIAL_MANAGER,
                        packageName = request.callingAppInfo?.packageName,
                        webDomain = null,
                        origin = null,
                        usernameHint = null,
                        hasPasswordField = true
                    )
                )
            },
            onSuccess = { queryResult ->
                val credentialEntries = passwordOptions.flatMap { option ->
                    queryResult.credentials
                        .filter { credential ->
                            credential.username.isNotBlank() &&
                                (option.allowedUserIds.isEmpty() ||
                                    credential.username in option.allowedUserIds ||
                                    credential.id in option.allowedUserIds)
                        }
                        .map { credential ->
                            PasswordCredentialEntry(
                                context = applicationContext,
                                username = credential.username,
                                pendingIntent = createPasswordGetPendingIntent(credential.id),
                                beginGetPasswordOption = option,
                                displayName = credential.displayName,
                                icon = Icon.createWithResource(this, R.drawable.ic_autofill_provider),
                                isAutoSelectAllowed = !credential.locked && credential.password != null,
                                affiliatedDomain = request.callingAppInfo?.packageName
                            )
                        }
                }

                val authenticationActions = if (queryResult.vaultLocked) {
                    listOf(
                        AuthenticationAction(
                            title = unlockActionTitle(),
                            pendingIntent = createCredentialUnlockPendingIntent()
                        )
                    )
                } else {
                    emptyList()
                }

                if (credentialEntries.isEmpty() && authenticationActions.isEmpty()) {
                    callback.onError(GetCredentialUnknownException("No matching passwords"))
                    return@runBackendCall
                }

                callback.onResult(
                    BeginGetCredentialResponse(
                        credentialEntries = credentialEntries,
                        authenticationActions = authenticationActions
                    )
                )
            },
            onError = { error ->
                callback.onError(
                    GetCredentialUnknownException(error.message ?: "Could not load passwords")
                )
            }
        )
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    private fun createPasswordSavePendingIntent(): PendingIntent {
        val intent = Intent(this, credentialSaveActivityClass())

        return PendingIntent.getActivity(
            this,
            PASSWORD_SAVE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createPasswordGetPendingIntent(credentialId: String): PendingIntent {
        val intent = Intent(this, credentialGetActivityClass())
            .setIdentifier(credentialId)
            .putExtra(CredentialGetActivity.EXTRA_CREDENTIAL_ID, credentialId)

        return PendingIntent.getActivity(
            this,
            PASSWORD_GET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createCredentialUnlockPendingIntent(): PendingIntent {
        val intent = Intent(this, credentialUnlockActivityClass())

        return PendingIntent.getActivity(
            this,
            PASSWORD_UNLOCK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val PASSWORD_SAVE_REQUEST_CODE = 28042
        const val PASSWORD_GET_REQUEST_CODE = 28043
        const val PASSWORD_UNLOCK_REQUEST_CODE = 28044
    }
}
