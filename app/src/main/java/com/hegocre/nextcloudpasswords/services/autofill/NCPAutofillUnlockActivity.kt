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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.activities.observeSsoReauthenticationRequired
import com.hegocre.nextcloudpasswords.ui.activities.openPasswordsWebUnlock
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel

class NCPAutofillUnlockActivity : FragmentActivity() {
    private val passwordsViewModel by viewModels<PasswordsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserController.getInstance(this).isLoggedIn) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        observeSsoReauthenticationRequired(
            passwordsViewModel = passwordsViewModel,
            replayIntent = null,
            beforeReauthentication = { setResult(RESULT_CANCELED) }
        )

        val searchHint = intent.getStringExtra(NCPAutofillService.AUTOFILL_SEARCH_HINT).orEmpty()

        enableEdgeToEdge()

        setContent {
            val showLockedAccountDialog by
                passwordsViewModel.clientDeauthorized.observeAsState(false)
            NCPAppLockWrapper {
                NextcloudPasswordsApp(
                    passwordsViewModel = passwordsViewModel,
                    showLockedAccountDialog = showLockedAccountDialog,
                    onUnlockLockedAccount = {
                        openPasswordsWebUnlock(passwordsViewModel)
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onCancelLockedAccount = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onLogOut = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    isAutofillUnlockRequest = true,
                    defaultSearchQuery = searchHint,
                    onAutofillUnlockComplete = {
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }

}
