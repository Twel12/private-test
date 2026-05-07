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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import foundation.e.auto_fill.PasswordSaveResult
import timber.log.Timber

class NCPAutofillPendingSaveUnlockActivity : FragmentActivity() {

    private val passwordsViewModel by viewModels<PasswordsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pendingSave = NCPAutofillPendingSaveStore.fromIntent(intent)
        if (pendingSave == null || !UserController.getInstance(this).isLoggedIn) {
            Timber.d("missing pending save or logged-out user")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

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
                        setResult(
                            if (result.isSuccessfulSaveResult()) {
                                RESULT_OK
                            } else {
                                RESULT_CANCELED
                            }
                        )
                        finish()
                    },
                    onAutofillUnlockComplete = {
                        finish()
                    }
                )
            }
        }
    }

    private fun PasswordSaveResult.isSuccessfulSaveResult(): Boolean {
        return this == PasswordSaveResult.Saved ||
            this == PasswordSaveResult.DuplicateIgnored ||
            this is PasswordSaveResult.QueuedForRetry
    }
}
