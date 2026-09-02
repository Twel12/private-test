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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.lifecycleScope
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.NCPApplication
import foundation.e.autofill.PasswordRequestSource
import foundation.e.autofill.PasswordSaveRequest
import foundation.e.autofill.PasswordSaveResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

class NCPAutofillSaveInteractionActivity : ComponentActivity() {

    private val backend: NCPPasswordBackend by lazy {
        NCPApplication.passwordBackend(this) as NCPPasswordBackend
    }
    private lateinit var saveRequest: PasswordSaveRequest
    private val showSavingDialog = mutableStateOf(false)
    private val saveCandidates = mutableStateListOf<NCPAutofillSaveCandidate>()
    private val showSaveChoiceDialog = mutableStateOf(false)
    private val unlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Timber.d("pending save unlock resultCode=${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (showSavingDialog.value) {
                AutofillSavingDialog()
            }
            if (showSaveChoiceDialog.value) {
                AutofillSaveChoiceDialog(
                    candidates = saveCandidates,
                    allowCreateNew = saveRequest.username?.isNotBlank() == true,
                    onSelectCandidate = { candidate ->
                        showSaveChoiceDialog.value = false
                        saveWithSelection(candidate.id, createNew = false)
                    },
                    onCreateNew = {
                        showSaveChoiceDialog.value = false
                        saveWithSelection(selectedCredentialId = null, createNew = true)
                    },
                    onDismissRequest = {
                        showSaveChoiceDialog.value = false
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }

        val request = intent.toPasswordSaveRequest()
        if (request == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        saveRequest = request
        Timber.d(
            "opened package=**, usernamePresent=${saveRequest.username?.isNotBlank() == true}, " +
                "identityKeyPresent=${saveRequest.identityKey?.isNotBlank() == true}, " +
                "source=${saveRequest.source}"
        )

        showSavingUi()
        launchBackendCall {
            val candidates = backend.saveInteractionCandidates(saveRequest)
            Timber.d("loaded save candidates count=${candidates.size}")
            if (candidates.isEmpty()) {
                saveNormally()
            } else {
                hideSavingUi()
                showChoiceDialog(candidates)
            }
        }
    }

    private fun showChoiceDialog(candidates: List<NCPAutofillSaveCandidate>) {
        saveCandidates.clear()
        saveCandidates.addAll(candidates)
        showSaveChoiceDialog.value = true
    }

    private fun saveNormally() {
        Timber.d("saveNormally")
        showSavingUi()
        launchBackendCall {
            handleSaveResult(backend.save(saveRequest), selectedCredentialId = null, createNew = false)
        }
    }

    private fun saveWithSelection(
        selectedCredentialId: String?,
        createNew: Boolean
    ) {
        Timber.d("saveWithSelection selectedPresent=${selectedCredentialId.isNullOrBlank().not()} createNew=$createNew")
        showSavingUi()
        launchBackendCall {
            handleSaveResult(
                result = backend.saveFromUserInteraction(saveRequest, selectedCredentialId),
                selectedCredentialId = selectedCredentialId,
                createNew = createNew
            )
        }
    }

    private fun launchBackendCall(block: suspend () -> Unit) {
        lifecycleScope.launch {
            runCatching {
                block()
            }.onFailure { error ->
                when (error) {
                    is CancellationException -> throw error
                    else -> finishWithBackendFailure(error)
                }
            }
        }
    }

    private fun finishWithBackendFailure(error: Throwable) {
        Timber.e(error, "Autofill save interaction backend call failed")
        hideSavingUi()
        Toast.makeText(
            this,
            getString(R.string.error_password_saving_failed),
            Toast.LENGTH_LONG
        ).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    private suspend fun handleSaveResult(
        result: PasswordSaveResult,
        selectedCredentialId: String?,
        createNew: Boolean
    ) {
        when (result) {
            PasswordSaveResult.Saved,
            PasswordSaveResult.DuplicateIgnored,
            is PasswordSaveResult.QueuedForRetry -> {
                hideSavingUi()
                setResult(RESULT_OK)
                finish()
            }

            PasswordSaveResult.NeedsUnlock -> {
                Timber.d("save needs unlock; opening app unlock")
                openAppUnlock(
                    selectedCredentialId = selectedCredentialId,
                    createNew = createNew
                )
            }

            is PasswordSaveResult.NeedsUserInteraction -> {
                hideSavingUi()
                Toast.makeText(
                    this@NCPAutofillSaveInteractionActivity,
                    result.reason ?: getString(R.string.autofill_save_select_password_to_update),
                    Toast.LENGTH_LONG
                ).show()
                val candidates = backend.saveInteractionCandidates(saveRequest)
                if (candidates.isEmpty()) {
                    setResult(RESULT_CANCELED)
                    finish()
                } else {
                    showChoiceDialog(candidates)
                }
            }

            is PasswordSaveResult.Failed -> {
                hideSavingUi()
                Toast.makeText(
                    this@NCPAutofillSaveInteractionActivity,
                    result.message ?: getString(R.string.error_password_saving_failed),
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun openAppUnlock(
        selectedCredentialId: String?,
        createNew: Boolean
    ) {
        Timber.d("openAppUnlock selectedPresent=${selectedCredentialId.isNullOrBlank().not()} createNew=$createNew")
        hideSavingUi()
        val unlockIntent = NCPAutofillPendingSaveStore.putExtras(
            intent = Intent(this, NCPAutofillPendingSaveUnlockActivity::class.java)
                .putExtra(
                    NCPAutofillService.AUTOFILL_SEARCH_HINT,
                    saveRequest.webDomain ?: saveRequest.packageName?.substringAfterLast('.').orEmpty()
                ),
            request = saveRequest,
            selectedCredentialId = selectedCredentialId,
            createNew = createNew
        )
        unlockLauncher.launch(unlockIntent)
    }

    override fun onDestroy() {
        hideSavingUi()
        super.onDestroy()
    }

    private fun showSavingUi() {
        showSavingDialog.value = true
    }

    private fun hideSavingUi() {
        showSavingDialog.value = false
    }

    companion object {
        private const val EXTRA_SOURCE = "foundation.e.passwords.autofill.EXTRA_SOURCE"
        private const val EXTRA_PACKAGE_NAME = "foundation.e.passwords.autofill.EXTRA_PACKAGE_NAME"
        private const val EXTRA_WEB_DOMAIN = "foundation.e.passwords.autofill.EXTRA_WEB_DOMAIN"
        private const val EXTRA_ORIGIN = "foundation.e.passwords.autofill.EXTRA_ORIGIN"
        private const val EXTRA_USERNAME = "foundation.e.passwords.autofill.EXTRA_USERNAME"
        private const val EXTRA_PASSWORD = "foundation.e.passwords.autofill.EXTRA_PASSWORD"
        private const val EXTRA_IS_WEB_ORIGIN_REQUEST =
            "foundation.e.passwords.autofill.EXTRA_IS_WEB_ORIGIN_REQUEST"
        private const val EXTRA_IDENTITY_KEY =
            "foundation.e.passwords.autofill.EXTRA_IDENTITY_KEY"

        fun intent(context: Context, request: PasswordSaveRequest): Intent {
            return Intent(context, NCPAutofillSaveInteractionActivity::class.java)
                .putExtra(EXTRA_SOURCE, request.source.name)
                .putExtra(EXTRA_PACKAGE_NAME, request.packageName)
                .putExtra(EXTRA_WEB_DOMAIN, request.webDomain)
                .putExtra(EXTRA_ORIGIN, request.origin)
                .putExtra(EXTRA_USERNAME, request.username)
                .putExtra(EXTRA_PASSWORD, request.password)
                .putExtra(EXTRA_IS_WEB_ORIGIN_REQUEST, request.isWebOriginRequest)
                .putExtra(EXTRA_IDENTITY_KEY, request.identityKey)
        }

        private fun Intent.toPasswordSaveRequest(): PasswordSaveRequest? {
            val password = getStringExtra(EXTRA_PASSWORD) ?: return null
            val source = getStringExtra(EXTRA_SOURCE)
                ?.let { runCatching { PasswordRequestSource.valueOf(it) }.getOrNull() }
                ?: PasswordRequestSource.AUTOFILL
            return PasswordSaveRequest(
                source = source,
                packageName = getStringExtra(EXTRA_PACKAGE_NAME),
                webDomain = getStringExtra(EXTRA_WEB_DOMAIN),
                origin = getStringExtra(EXTRA_ORIGIN),
                username = getStringExtra(EXTRA_USERNAME),
                password = password,
                isWebOriginRequest = getBooleanExtra(EXTRA_IS_WEB_ORIGIN_REQUEST, false),
                identityKey = getStringExtra(EXTRA_IDENTITY_KEY)
            )
        }
    }
}
