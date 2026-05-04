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
import android.service.autofill.FillResponse
import android.text.InputType
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import timber.log.Timber

abstract class AutofillDatasetAuthActivity : ComponentActivity() {
    protected abstract fun passwordBackend(): MurenaPasswordBackend

    private var packageName: String? = null
    private var webDomain: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usernameIds = IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_USERNAME_IDS,
            AutofillId::class.java
        ) ?: arrayListOf()
        val passwordIds = IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_PASSWORD_IDS,
            AutofillId::class.java
        ) ?: arrayListOf()
        val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID)
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)

        if (intent.getBooleanExtra(EXTRA_UNLOCK_ONLY, false)) {
            if (usernameIds.isEmpty() && passwordIds.isEmpty()) {
                setResult(RESULT_CANCELED)
                finish()
                return
            }
            requestVaultUnlock(
                request = VaultUnlockRequest(
                    source = PasswordRequestSource.AUTOFILL,
                    packageName = packageName,
                    webDomain = webDomain,
                    origin = null
                ),
                onResult = { result ->
                    when (result) {
                        VaultUnlockResult.Unlocked -> {
                            finishWithUnlockedResponse(usernameIds, passwordIds)
                        }

                        VaultUnlockResult.Canceled,
                        is VaultUnlockResult.Failed -> {
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    }
                }
            )
            return
        }

        if ((usernameIds.isEmpty() && passwordIds.isEmpty()) || credentialId.isNullOrBlank()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        resolveCredential(credentialId, usernameIds, passwordIds, triedUnlock = false)
    }

    private fun resolveCredential(
        credentialId: String,
        usernameIds: List<AutofillId>,
        passwordIds: List<AutofillId>,
        triedUnlock: Boolean
    ) {
        this.runBackendCall(
            call = {
                passwordBackend().resolve(
                    PasswordResolveRequest(
                        source = PasswordRequestSource.AUTOFILL,
                        credentialId = credentialId,
                        packageName = packageName,
                        webDomain = webDomain,
                        origin = null
                    )
                )
            },
            onSuccess = { credential ->
                if (credential == null ||
                    (passwordIds.isNotEmpty() && credential.password.isNullOrBlank())
                ) {
                    if (triedUnlock) {
                        setResult(RESULT_CANCELED)
                        finish()
                    } else {
                        requestVaultUnlock(
                            request = VaultUnlockRequest(
                                source = PasswordRequestSource.AUTOFILL,
                                packageName = packageName,
                                webDomain = webDomain,
                                origin = null
                            ),
                            onResult = { result ->
                                when (result) {
                                    VaultUnlockResult.Unlocked -> {
                                        resolveCredential(
                                            credentialId,
                                            usernameIds,
                                            passwordIds,
                                            triedUnlock = true
                                        )
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

                finishWithCredential(credential, usernameIds, passwordIds)
            },
            onError = { error ->
                Timber.e(error,"Failed to resolve autofill credential" )
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
                this@AutofillDatasetAuthActivity.runBackendCall(
                    call = {
                        passwordBackend().unlock(
                            request.copy(secret = passwordInput.text?.toString())
                        )
                    },
                    onSuccess = onResult,
                    onError = { error ->
                        Timber.e(error,"Failed to unlock vault")
                        onResult(VaultUnlockResult.Failed(error.message))
                    }
                )
            }
            .setOnCancelListener {
                onResult(VaultUnlockResult.Canceled)
            }
            .show()
    }

    private fun finishWithUnlockedResponse(
        usernameIds: List<AutofillId>,
        passwordIds: List<AutofillId>
    ) {
        this.runBackendCall(
            call = {
                passwordBackend().query(
                    PasswordQuery(
                        source = PasswordRequestSource.AUTOFILL,
                        packageName = packageName,
                        webDomain = webDomain,
                        origin = null,
                        usernameHint = null,
                        hasPasswordField = passwordIds.isNotEmpty()
                    )
                )
            },
            onSuccess = { queryResult ->
                val credentials = if (passwordIds.isNotEmpty()) {
                    queryResult.credentials.filter { credential ->
                        !credential.locked && !credential.password.isNullOrBlank()
                    }
                } else {
                    queryResult.credentials.filter { credential ->
                        !credential.locked && credential.username.isNotBlank()
                    }
                }

                if (credentials.isEmpty()) {
                    setResult(RESULT_CANCELED)
                    finish()
                    return@runBackendCall
                }

                val responseBuilder = FillResponse.Builder()
                credentials.forEach { credential ->
                    responseBuilder.addDataset(
                        MurenaAutoFillService.buildCredentialDataset(
                            context = this,
                            authActivityClass = this.javaClass,
                            credential = credential,
                            usernameIds = usernameIds,
                            passwordIds = passwordIds
                        )
                    )
                }

                val result = Intent().apply {
                    putExtra(
                        AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                        responseBuilder.build()
                    )
                }
                setResult(RESULT_OK, result)
                finish()
            },
            onError = { error ->
                Timber.e(error,"Failed to query unlocked autofill credentials")
                setResult(RESULT_CANCELED)
                finish()
            }
        )
    }

    private fun finishWithCredential(
        credential: PasswordEntry,
        usernameIds: List<AutofillId>,
        passwordIds: List<AutofillId>
    ) {
        val result = Intent().apply {
            putExtra(
                AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                MurenaAutoFillService.buildCredentialDataset(
                    context = this@AutofillDatasetAuthActivity,
                    authActivityClass = this@AutofillDatasetAuthActivity.javaClass,
                    credential = credential,
                    usernameIds = usernameIds,
                    passwordIds = passwordIds,
                    label = credential.label
                )
            )
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_USERNAME_IDS = "foundation.e.auto_fill.EXTRA_USERNAME_IDS"
        const val EXTRA_PASSWORD_IDS = "foundation.e.auto_fill.EXTRA_PASSWORD_IDS"
        const val EXTRA_CREDENTIAL_ID = "foundation.e.auto_fill.EXTRA_CREDENTIAL_ID"
        const val EXTRA_PACKAGE_NAME = "foundation.e.auto_fill.EXTRA_PACKAGE_NAME"
        const val EXTRA_WEB_DOMAIN = "foundation.e.auto_fill.EXTRA_WEB_DOMAIN"
        const val EXTRA_UNLOCK_ONLY = "foundation.e.auto_fill.EXTRA_UNLOCK_ONLY"
        private const val TAG = "AutofillDatasetAuth"
    }
}
