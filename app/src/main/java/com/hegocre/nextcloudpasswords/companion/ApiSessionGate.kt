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
package com.hegocre.nextcloudpasswords.companion

import android.content.Context
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.session.withTemporarySession
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.SecureMasterPasswordStore
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import foundation.e.passwords.companion.UserAction
import foundation.e.passwords.companion.UserReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ApiSessionGate(context: Context) : SessionGate {

    private val appContext = context.applicationContext

    override suspend fun <T> withSession(block: suspend () -> T): GateResult<T> {
        if (!UserController.getInstance(appContext).isLoggedIn) {
            return GateResult.Blocked(UserAction.SIGN_IN, UserReason.NO_ACCOUNT)
        }
        if (isSyncDisabled()) return syncDisabledBlocker()

        // One gated call at a time: a concurrent call must not see, or close, another call's temporary session.
        return sessionMutex.withLock { openAndRun(block) }
    }

    private suspend fun <T> openAndRun(block: suspend () -> T): GateResult<T> {
        val api = ApiController.getInstance(appContext)
        val masterPassword = MasterPasswordMemoryStore.get()?.takeIf { it.isNotBlank() }
        val result = api.withTemporarySession(masterPassword = masterPassword, clearStoredKeychainOnClose = false) {
            if (api.isEndToEndEncryptionKeyAvailable()) GateBox(block()) else null
        }

        return result.toGateResult {
            MasterPasswordMemoryStore.clear()
            SecureMasterPasswordStore(appContext).clear()
        }
    }

    // Keyed on the SSO account, not the OkHttp instance type, which is only switched to SSO once ApiController exists.
    private fun isSyncDisabled(): Boolean = runCatching {
        SsoAccount.getCurrentSingleSignOnAccount(appContext) != null &&
            !SsoAccount.isCurrentMurenaSyncEnabled(appContext)
    }.getOrDefault(false)

    private companion object {
        val sessionMutex = Mutex()
    }
}
