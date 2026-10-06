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
