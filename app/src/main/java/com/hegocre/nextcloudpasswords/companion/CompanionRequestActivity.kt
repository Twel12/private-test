package com.hegocre.nextcloudpasswords.companion

import android.accounts.Account
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.ui.activities.BaseAutoLogin
import com.hegocre.nextcloudpasswords.ui.activities.MurenaAccountSyncSettings
import com.hegocre.nextcloudpasswords.ui.activities.MurenaSyncDisabledFlow
import com.hegocre.nextcloudpasswords.ui.activities.observeAppPasswordRequired
import com.hegocre.nextcloudpasswords.ui.activities.observeSsoReauthenticationRequired
import com.hegocre.nextcloudpasswords.ui.activities.openPasswordsWebUnlock
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.migration.launchE2eeMigration
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import foundation.e.passwords.companion.CompanionCodec
import foundation.e.passwords.companion.CompanionProtocol
import foundation.e.passwords.companion.CompanionResult
import foundation.e.passwords.companion.Failed
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.UserAction
import kotlinx.coroutines.launch

class CompanionRequestActivity : FragmentActivity() {

    private val passwordsViewModel by viewModels<PasswordsViewModel>()
    private val vault by lazy { CompanionComponents.vault(this) }
    private val token: String? by lazy { intent.getStringExtra(CompanionIntents.EXTRA_TOKEN) }

    private var unlockShown = false
    private var waitingForUnlockInWeb = false
    private var running = false
    private var finished = false
    private val conflictCandidates = mutableStateOf<List<VaultEntry>?>(null)

    private val appPasswordLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            passwordsViewModel.onReturnedFromAppPasswordFlow()
        }

    private val syncDisabled: MurenaSyncDisabledFlow by lazy {
        MurenaSyncDisabledFlow(
            launchSettings = { account -> MurenaAccountSyncSettings.open(this, account) },
            onSyncEnabled = { baseAutoLogin.start() },
            onStillDisabledOrLaunchFailed = { finishWith(Failed(FailureCode.CANCELED)) },
        )
    }

    private val baseAutoLogin: BaseAutoLogin by lazy {
        object : BaseAutoLogin(this@CompanionRequestActivity) {
            override fun accountExist() = showUnlock()

            override fun onLoginSuccess() {
                if (unlockShown) restartAfterReauthentication() else showUnlock()
            }

            override fun signatureError() = finishWith(Failed(FailureCode.NOT_ALLOWED))

            override fun accountUnavailable() {
                Toast.makeText(
                    this@CompanionRequestActivity,
                    R.string.error_session_no_account,
                    Toast.LENGTH_LONG
                ).show()
                finishWith(Failed(FailureCode.CANCELED))
            }

            override fun syncDisabled(account: Account) {
                Toast.makeText(
                    this@CompanionRequestActivity,
                    R.string.error_sso_sync_disabled,
                    Toast.LENGTH_LONG
                ).show()
                syncDisabled.launch(account)
            }

            override fun ssoFailed() = finishWith(Failed(FailureCode.CANCELED))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWith(Failed(FailureCode.CANCELED))
        })
        if (PendingRequestStore.shared.peek(token) == null) {
            finishWith(Failed(FailureCode.EXPIRED))
            return
        }
        baseAutoLogin.start()
    }

    private fun showUnlock() {
        if (unlockShown) return
        unlockShown = true

        observeSsoReauthenticationRequired(
            passwordsViewModel = passwordsViewModel,
            reauthenticate = { baseAutoLogin.start(forceSsoReauthentication = true) },
        )
        observeAppPasswordRequired(passwordsViewModel) { intent ->
            if (!SsoAccount.hasValidAccountManagerSignature(this)) {
                passwordsViewModel.onAppPasswordFlowUnavailable()
                Toast.makeText(this, R.string.two_factor_app_password_unavailable, Toast.LENGTH_LONG).show()
                return@observeAppPasswordRequired
            }
            try {
                appPasswordLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                passwordsViewModel.onAppPasswordFlowUnavailable()
                Toast.makeText(this, R.string.two_factor_app_password_unavailable, Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            val showLockedAccountDialog by passwordsViewModel.clientDeauthorized.observeAsState(false)
            val showE2eeMigrationDialog by passwordsViewModel.showE2eeMigrationDialog.collectAsState()
            val candidates by conflictCandidates
            NCPAppLockWrapper {
                NextcloudPasswordsApp(
                    passwordsViewModel = passwordsViewModel,
                    onLogOut = { finishWith(Failed(FailureCode.CANCELED)) },
                    onCancelMasterPasswordDialog = { finishWith(Failed(FailureCode.CANCELED)) },
                    showLockedAccountDialog = showLockedAccountDialog,
                    onUnlockLockedAccount = ::unlockAccountInWeb,
                    onCancelLockedAccount = { finishWith(Failed(FailureCode.CANCELED)) },
                    showE2eeMigrationDialog = showE2eeMigrationDialog,
                    onStartE2eeMigration = { launchE2eeMigration(passwordsViewModel) },
                    onCancelE2eeMigration = { finishWith(Failed(FailureCode.CANCELED)) },
                )
                candidates?.let { entries ->
                    CompanionConflictDialog(
                        candidates = entries,
                        onKeep = ::onEntryChosen,
                        onCancel = { finishWith(Failed(FailureCode.CANCELED)) },
                    )
                }
            }
        }

        // The session reopens after an E2EE migration, so watch it here.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                passwordsViewModel.sessionOpen.collect { open -> if (open) tryComplete() }
            }
        }
    }

    private fun tryComplete() {
        if (running || finished || conflictCandidates.value != null) return
        // No E2EE key yet: the migration dialog is on screen.
        if (!ApiController.getInstance(this).isEndToEndEncryptionKeyAvailable()) return
        val request = PendingRequestStore.shared.peek(token) ?: return finishWith(Failed(FailureCode.EXPIRED))
        running = true
        lifecycleScope.launch {
            val outcome = CompanionRunner.run(vault, request)
            running = false
            if (outcome is VaultOutcome.NeedsUser) {
                handleBlocker(request, outcome.action)
            } else {
                finishWith(CompanionRunner.toResult(outcome))
            }
        }
    }

    // A further blocker is cleared on this screen, never handed back to the app.
    private suspend fun handleBlocker(request: PendingRequest, action: String) {
        when (action) {
            UserAction.CHOOSE_ENTRY -> conflictCandidates.value = vault.candidates(request.owner)
            UserAction.SIGN_IN -> baseAutoLogin.start(forceSsoReauthentication = true)
            UserAction.ENABLE_SYNC -> baseAutoLogin.start()
            UserAction.UNLOCK_ON_DEVICE -> passwordsViewModel.requestMasterPassword()
            UserAction.ACTION_ON_WEB -> passwordsViewModel.sync()
            else -> finishWith(Failed(FailureCode.UNKNOWN))
        }
    }

    private fun onEntryChosen(keepId: String) {
        val request = PendingRequestStore.shared.peek(token) ?: return finishWith(Failed(FailureCode.EXPIRED))
        conflictCandidates.value = null
        lifecycleScope.launch {
            if (vault.choose(request.owner, keepId)) tryComplete() else finishWith(Failed(FailureCode.SERVER))
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
        if (openPasswordsWebUnlock(passwordsViewModel)) waitingForUnlockInWeb = true
    }

    // Re-authentication gives the account a new ApiController that this screen's view model never
    // sees. A fresh screen picks it up, and FORWARD_RESULT still answers the original caller.
    private fun restartAfterReauthentication() {
        startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT))
        finish()
    }

    private fun finishWith(result: CompanionResult) {
        if (finished) return
        finished = true
        PendingRequestStore.shared.remove(token)
        setResult(RESULT_OK, Intent().putExtra(CompanionProtocol.EXTRA_RESULT, CompanionCodec.encode(result)))
        finish()
    }
}
