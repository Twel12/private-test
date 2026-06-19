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
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.ui.activities.observeSsoReauthenticationRequired
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import com.hegocre.nextcloudpasswords.utils.ActionsConst
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import foundation.e.autofill.isSuccessful
import timber.log.Timber

class NCPAutofillPendingSaveUnlockActivity : FragmentActivity() {

    private val passwordsViewModel by viewModels<PasswordsViewModel>()
    private var pendingSave: NCPAutofillPendingSaveStore.PendingSave? = null
    private var resuming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(NCPAutofillPendingSaveContinuation.EXTRA_TOKEN)
        val loggedIn = UserController.getInstance(this).isLoggedIn

        if (token != null) {
            handleResume(token, loggedIn)
        } else {
            handleFreshSave(loggedIn)
        }
    }

    private fun handleResume(token: String, loggedIn: Boolean) {
        resuming = true
        val save = NCPAutofillPendingSaveContinuation.get(token)
        if (save == null || !loggedIn) {
            cancelAndFinish()
            return
        }
        pendingSave = save
        showUnlockContent()
    }

    private fun handleFreshSave(loggedIn: Boolean) {
        val save = NCPAutofillPendingSaveStore.fromIntent(intent)
        if (save == null) {
            cancelAndFinish()
            return
        }

        if (loggedIn) {
            pendingSave = save
            showUnlockContent()
            return
        }

        if (NCPAutofillPendingSaveContinuation.isActive()) {
            cancelAndFinish()
        } else {
            startLoginForSave(save)
        }
    }

    private fun startLoginForSave(save: NCPAutofillPendingSaveStore.PendingSave) {
        val token = NCPAutofillPendingSaveContinuation.begin(save)
        OkHttpRequestInterface.useBasic(null)
        startActivity(
            Intent(ActionsConst.CLASSIC_LOGIN)
                .setPackage(packageName)
                .putExtra(NCPAutofillPendingSaveContinuation.EXTRA_TOKEN, token)
        )
        finish()
    }

    private fun cancelAndFinish() {
        Timber.d("missing pending save or logged-out user")
        if (resuming) {
            NCPAutofillPendingSaveContinuation.clear()
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun showUnlockContent() {
        val pendingSave = pendingSave ?: run {
            cancelAndFinish()
            return
        }

        observeSsoReauthenticationRequired(
            passwordsViewModel = passwordsViewModel,
            replayIntent = null,
            beforeReauthentication = { setResult(RESULT_CANCELED) }
        )

        enableEdgeToEdge()

        setContent {
            NCPAppLockWrapper {
                NextcloudPasswordsApp(
                    passwordsViewModel = passwordsViewModel,
                    onLogOut = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    isAutofillUnlockRequest = true,
                    pendingAutofillSave = pendingSave,
                    defaultSearchQuery = pendingSave.request.webDomain
                        ?: pendingSave.request.packageName?.substringAfterLast('.').orEmpty(),
                    onPendingAutofillSaveComplete = { result ->
                        NCPAutofillPendingSaveContinuation.clear()
                        setResult(if (result.isSuccessful) RESULT_OK else RESULT_CANCELED)
                        finish()
                    },
                    onAutofillUnlockComplete = {
                        finish()
                    }
                )
            }
        }
    }
}
