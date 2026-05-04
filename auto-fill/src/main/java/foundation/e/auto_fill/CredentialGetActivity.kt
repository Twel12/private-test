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

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderGetCredentialRequest
import timber.log.Timber

abstract class CredentialGetActivity : ComponentActivity() {
    protected abstract fun passwordBackend(): MurenaPasswordBackend

    private var credentialId: String? = null
    private var packageName: String? = null
    private var providerRequest: ProviderGetCredentialRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        providerRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val getRequest = providerRequest
        if (getRequest == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID)
        packageName = getRequest.callingAppInfo.packageName
        val selectedCredentialId = credentialId
        if (selectedCredentialId.isNullOrBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        resolveCredential(selectedCredentialId, triedUnlock = false)
    }

    private fun resolveCredential(
        credentialId: String,
        triedUnlock: Boolean
    ) {
        this.runBackendCall(
            call = {
                passwordBackend().resolve(
                    PasswordResolveRequest(
                        source = PasswordRequestSource.CREDENTIAL_MANAGER,
                        credentialId = credentialId,
                        packageName = packageName,
                        webDomain = null,
                        origin = null
                    )
                )
            },
            onSuccess = { credential ->
                if (credential?.password.isNullOrBlank()) {
                    if (triedUnlock) {
                        setResult(RESULT_CANCELED)
                        finish()
                    } else {
                        requestVaultUnlock(
                            request = VaultUnlockRequest(
                                source = PasswordRequestSource.CREDENTIAL_MANAGER,
                                packageName = packageName,
                                webDomain = null,
                                origin = null
                            ),
                            onResult = { result ->
                                when (result) {
                                    VaultUnlockResult.Unlocked -> {
                                        resolveCredential(credentialId, triedUnlock = true)
                                    }

                                    VaultUnlockResult.Canceled,
                                    is VaultUnlockResult.Failed -> {
                                        setResult(RESULT_CANCELED)
                                        finish()
                                    }
                                }
                            }
                        )
                    }
                    return@runBackendCall
                }

                Timber.d("Returning password credential for $packageName" )

                val getRequest = providerRequest
                if (getRequest == null) {
                    setResult(RESULT_CANCELED)
                    finish()
                    return@runBackendCall
                }

                val result = Intent()
                PendingIntentHandler.setGetCredentialResponse(
                    result,
                    GetCredentialResponse(
                        PasswordCredential(
                            id = credential.username,
                            password = credential.password
                        )
                    ),
                    getRequest
                )
                passwordBackend().reportSafely(
                    PasswordEvent.CredentialSelected(
                        source = PasswordRequestSource.CREDENTIAL_MANAGER,
                        credentialId = credential.id,
                        packageName = packageName,
                        webDomain = null,
                        origin = null
                    )
                )
                setResult(RESULT_OK, result)
                finish()
            },
            onError = { error ->
                Timber.e(error,"Failed to resolve password credential" )
                setResult(RESULT_CANCELED)
                finish()
            }
        )
    }

    protected open fun requestVaultUnlock(
        request: VaultUnlockRequest,
        onResult: (VaultUnlockResult) -> Unit
    ) {
        val passwordInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.unlock_vault_title)
            .setMessage(R.string.unlock_vault_message)
            .setView(passwordInput)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onResult(VaultUnlockResult.Canceled)
            }
            .setPositiveButton(R.string.unlock_vault_button) { _, _ ->
                this@CredentialGetActivity.runBackendCall(
                    call = {
                        passwordBackend().unlock(
                            request.copy(secret = passwordInput.text?.toString())
                        )
                    },
                    onSuccess = onResult,
                    onError = { error ->
                        Timber.e(error,"Failed to unlock vault" )
                        onResult(VaultUnlockResult.Failed(error.message))
                    }
                )
            }
            .setOnCancelListener {
                onResult(VaultUnlockResult.Canceled)
            }
            .show()
    }

    companion object {
        const val EXTRA_CREDENTIAL_ID = "foundation.e.auto_fill.EXTRA_CREDENTIAL_ID"
        private const val TAG = "CredentialGet"
    }
}
