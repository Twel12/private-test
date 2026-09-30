package com.hegocre.nextcloudpasswords.ui.activities

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.migration.launchE2eeMigration
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import foundation.e.data.SetupConsent
import foundation.e.data.SetupResponse
import kotlinx.coroutines.launch

class BackupAppSetupActivity : FragmentActivity() {

    companion object {
        const val TAG = "BackupAppSetupActivity"
    }

    private val passwordsViewModel by viewModels<PasswordsViewModel>()

    private var unlockShown = false
    private var waitingForUnlockInWeb = false

    private val syncDisabled: MurenaSyncDisabledFlow by lazy {
        MurenaSyncDisabledFlow(
            launchSettings = { account -> MurenaAccountSyncSettings.open(this, account) },
            onSyncEnabled = { baseAutoLogin.start() },
            onStillDisabledOrLaunchFailed = { finishCanceled() },
        )
    }

    private val baseAutoLogin: BaseAutoLogin by lazy {
        object : BaseAutoLogin(this@BackupAppSetupActivity) {
            override fun accountExist() = showUnlock()

            override fun onLoginSuccess() {
                if (unlockShown) restartAfterReauthentication() else showUnlock()
            }

            override fun signatureError() = response(SetupResponse.Failed)

            override fun accountUnavailable() {
                Log.d(TAG, "murena account is not available, leaving e2ee setup")
                response(SetupResponse.AccountUnavailable)
            }

            override fun syncDisabled(account: Account) {
                Toast.makeText(
                    this@BackupAppSetupActivity,
                    R.string.error_sso_sync_disabled,
                    Toast.LENGTH_LONG
                ).show()
                syncDisabled.launch(account)
            }

            override fun ssoFailed() = response(SetupResponse.Failed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baseAutoLogin.start()
    }

    private fun showUnlock() {
        if (unlockShown) return
        unlockShown = true

        observeSsoReauthenticationRequired(
            passwordsViewModel = passwordsViewModel,
            reauthenticate = { baseAutoLogin.start(forceSsoReauthentication = true) },
        )

        setContent {
            val showLockedAccountDialog by
                passwordsViewModel.clientDeauthorized.observeAsState(false)
            val showE2eeMigrationDialog by passwordsViewModel.showE2eeMigrationDialog.collectAsState()
            NCPAppLockWrapper {
                NextcloudPasswordsApp(
                    passwordsViewModel = passwordsViewModel,
                    onLogOut = { response(SetupResponse.AccountUnavailable) },
                    onCancelMasterPasswordDialog = ::finishCanceled,
                    showLockedAccountDialog = showLockedAccountDialog,
                    onUnlockLockedAccount = ::unlockAccountInWeb,
                    onCancelLockedAccount = ::finishCanceled,
                    showE2eeMigrationDialog = showE2eeMigrationDialog,
                    onStartE2eeMigration = { launchE2eeMigration(passwordsViewModel) },
                    onCancelE2eeMigration = ::finishCanceled
                )
            }
        }

        // The session reopens after an E2EE migration, so watch it here.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                passwordsViewModel.sessionOpen.collect { open ->
                    if (open) onUnlocked()
                }
            }
        }
    }

    // Re-authentication gives the account a new ApiController that this screen's view model never
    // sees. A fresh screen picks it up, and FORWARD_RESULT still answers the original caller.
    private fun restartAfterReauthentication() {
        startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT))
        finish()
    }

    // No E2EE key yet: stay open so the migration dialog can run.
    private fun onUnlocked() {
        if (ApiController.getInstance(this).isEndToEndEncryptionKeyAvailable()) {
            response(SetupResponse.Success)
        }
    }

    override fun onResume() {
        super.onResume()
        if (syncDisabled.onResume()) return
        if (!unlockShown) return
        if (waitingForUnlockInWeb) {
            waitingForUnlockInWeb = false
            passwordsViewModel.sync()
            return
        }
        passwordsViewModel.onAppResumedAfterMigration()
    }

    override fun onPause() {
        super.onPause()
        syncDisabled.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        baseAutoLogin.onActivityResult(requestCode, resultCode, data)
    }

    private fun unlockAccountInWeb() {
        if (openPasswordsWebUnlock(passwordsViewModel)) {
            waitingForUnlockInWeb = true
        }
    }

    private fun response(response: SetupResponse) {
        val code = if (response == SetupResponse.Success) RESULT_OK else RESULT_CANCELED
        setResult(code, Intent().putExtra(SetupConsent.EXTRA_SETUP_RESPONSE, response.ordinal))
        finish()
    }

    private fun finishCanceled() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
