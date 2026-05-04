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
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.provider.PendingIntentHandler

abstract class CredentialSaveConfirmationActivity : ComponentActivity() {
    protected abstract fun passwordBackend(): MurenaPasswordBackend

    protected open fun saveInteractionIntent(request: PasswordSaveRequest): Intent? = null

    protected open fun showSavingUi() = Unit

    protected open fun hideSavingUi() = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val providerRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val passwordRequest = providerRequest?.callingRequest as? CreatePasswordRequest

        if (providerRequest == null || passwordRequest == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val saveRequest = PasswordSaveRequest(
            source = PasswordRequestSource.CREDENTIAL_MANAGER,
            packageName = providerRequest.callingAppInfo.packageName,
            webDomain = null,
            origin = null,
            username = passwordRequest.id,
            password = passwordRequest.password
        )
        debugLog(TAG) {
            "onCreate save request package=${saveRequest.packageName}, " +
                "usernamePresent=${saveRequest.username?.isNotBlank() == true}, " +
                "passwordPresent=${saveRequest.password.isNotBlank()}"
        }
        showSavingUi()
        savePassword(saveRequest, triedUnlock = false)
    }

    private fun savePassword(
        saveRequest: PasswordSaveRequest,
        triedUnlock: Boolean
    ) {
        this.runBackendCall(
            call = {
                passwordBackend().save(saveRequest)
            },
            onSuccess = { saveResult ->
                runOnUiThread {
                    debugLog(TAG) { "save result=$saveResult triedUnlock=$triedUnlock" }
                    handleSaveResult(saveRequest, saveResult, triedUnlock)
                }
            },
            onError = { error ->
                runOnUiThread {
                    debugError(TAG, error) { "Credential Manager save failed" }
                    hideSavingUi()
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        )
    }

    private fun handleSaveResult(
        saveRequest: PasswordSaveRequest,
        saveResult: PasswordSaveResult,
        triedUnlock: Boolean
    ) {
        when (saveResult) {
            PasswordSaveResult.Saved,
            PasswordSaveResult.DuplicateIgnored,
            is PasswordSaveResult.QueuedForRetry -> {
                hideSavingUi()
                finishWithSuccess()
            }

            PasswordSaveResult.NeedsUnlock -> {
                if (triedUnlock) {
                    hideSavingUi()
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }


                debugLog(TAG) { "save needs unlock; falling back to vault unlock request" }
                hideSavingUi()
                requestVaultUnlock(
                    request = VaultUnlockRequest(
                        source = saveRequest.source,
                        packageName = saveRequest.packageName,
                        webDomain = saveRequest.webDomain,
                        origin = saveRequest.origin
                    ),
                    onResult = { result ->
                        when (result) {
                            VaultUnlockResult.Unlocked -> {
                                showSavingUi()
                                savePassword(saveRequest, triedUnlock = true)
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

            is PasswordSaveResult.Failed -> {
                debugWarn(TAG) { "Credential Manager save failed: ${saveResult.message}" }
                hideSavingUi()
                setResult(RESULT_CANCELED)
            }

            is PasswordSaveResult.NeedsUserInteraction -> {
                val intent = saveInteractionIntent(saveRequest)
                if (intent == null) {
                    debugWarn(TAG) {
                        "Credential Manager save needs user interaction but no intent is provided"
                    }
                    hideSavingUi()
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }

                hideSavingUi()
                @Suppress("DEPRECATION")
                startActivityForResult(intent, SAVE_INTERACTION_REQUEST_CODE)
                return
            }
        }
        finish()
    }

    @Deprecated("Uses legacy result API because credential provider pending intents use Activity results.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        debugLog(TAG) { "onActivityResult requestCode=$requestCode resultCode=$resultCode" }
        hideSavingUi()
        if (requestCode == SAVE_INTERACTION_REQUEST_CODE && resultCode == RESULT_OK) {
            finishWithSuccess()
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    private fun finishWithSuccess() {
        val result = Intent()
        PendingIntentHandler.setCreateCredentialResponse(
            result,
            CreatePasswordResponse()
        )
        setResult(RESULT_OK, result)
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
                this@CredentialSaveConfirmationActivity.runBackendCall(
                    call = {
                        passwordBackend().unlock(
                            request.copy(secret = passwordInput.text?.toString())
                        )
                    },
                    onSuccess = { result ->
                        runOnUiThread { onResult(result) }
                    },
                    onError = { error ->
                        runOnUiThread {
                            debugError(TAG, error) { "Failed to unlock vault" }
                            onResult(VaultUnlockResult.Failed(error.message))
                        }
                    }
                )
            }
            .setOnCancelListener {
                onResult(VaultUnlockResult.Canceled)
            }
            .show()
    }

    private companion object {
        private const val TAG = "CredentialSaveConfirm"
        private const val SAVE_INTERACTION_REQUEST_CODE = 31001
    }
}
