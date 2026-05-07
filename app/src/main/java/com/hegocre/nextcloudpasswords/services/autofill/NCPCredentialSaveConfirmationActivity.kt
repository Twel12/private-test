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

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.hegocre.nextcloudpasswords.NCPApplication
import foundation.e.auto_fill.CredentialSaveConfirmationActivity
import foundation.e.auto_fill.MurenaPasswordBackend
import foundation.e.auto_fill.PasswordSaveRequest
import foundation.e.auto_fill.VaultUnlockRequest
import foundation.e.auto_fill.VaultUnlockResult

class NCPCredentialSaveConfirmationActivity : CredentialSaveConfirmationActivity() {
    private val showSavingDialog = mutableStateOf(false)
    private var unlockResultCallback: ((VaultUnlockResult) -> Unit)? = null
    private val unlockActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = unlockResultCallback
        unlockResultCallback = null
        callback?.invoke(
            if (result.resultCode == RESULT_OK) {
                VaultUnlockResult.Unlocked
            } else {
                VaultUnlockResult.Canceled
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (showSavingDialog.value) {
                AutofillSavingDialog()
            }
        }
    }

    override fun passwordBackend(): MurenaPasswordBackend {
        return NCPApplication.passwordBackend(this)
    }

    override fun saveInteractionIntent(request: PasswordSaveRequest): Intent {
        return NCPAutofillSaveInteractionActivity.intent(this, request)
    }

    override fun showSavingUi() {
        showSavingDialog.value = true
    }

    override fun hideSavingUi() {
        showSavingDialog.value = false
    }

    override fun privilegedAppAllowlistJson(): String {
        return NCPCredentialManagerPrivilegedApps.json(this)
    }

    override fun requestVaultUnlock(
        request: VaultUnlockRequest,
        onResult: (VaultUnlockResult) -> Unit
    ) {
        unlockResultCallback = onResult
        unlockActivityLauncher.launch(
            Intent(this, NCPAutofillUnlockActivity::class.java)
                .putExtra(
                    NCPAutofillService.AUTOFILL_SEARCH_HINT,
                    request.webDomain ?: request.packageName?.substringAfterLast('.').orEmpty()
                )
        )
    }
}
