package com.hegocre.nextcloudpasswords.ui.activities

import android.app.assist.AssistStructure
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.hegocre.nextcloudpasswords.BuildConfig
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.services.autofill.AutofillHelper
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillMetadata
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillService
import com.hegocre.nextcloudpasswords.services.autofill.NCPPasswordBackend
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.migration.launchE2eeMigration
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import com.hegocre.nextcloudpasswords.utils.LogHelper
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import com.hegocre.nextcloudpasswords.utils.SsoOkHttpRequest
import foundation.e.autofill.PasswordSaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : FragmentActivity() {

    private val passwordsViewModel by viewModels<PasswordsViewModel>()

    // Launches the AccountManager 2FA app-password flow. When the user returns (grant done or
    // cancelled), refetch so a freshly stored app password takes effect and the prompt clears.
    private val appPasswordLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            passwordsViewModel.onReturnedFromAppPasswordFlow()
        }

    private val syncDisabled: MurenaSyncDisabledFlow by lazy {
        MurenaSyncDisabledFlow(
            launchSettings = { account -> MurenaAccountSyncSettings.open(this, account) },
            onSyncEnabled = { passwordsViewModel.sync() },
            onStillDisabledOrLaunchFailed = {
                passwordsViewModel.onMurenaSyncDisabledShown()
                finish()
            },
        )
    }

    private var waitingForUnlockInWeb = false

    private var migrationFlowEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG) LogHelper.getInstance()

        super.onCreate(savedInstanceState)
        if (!UserController.getInstance(this).isLoggedIn) {
            //try auto login, it will start regular login if it fails
            startActivity(AutoLoginActivity.intent(this, intent))
            finish()
            return
        }

        val autofillAssistStructure =
            IntentCompat.getParcelableExtra(
                intent,
                AutofillManager.EXTRA_ASSIST_STRUCTURE,
                AssistStructure::class.java
            )

        val autofillSelectionState = resolveAutofillSelectionState(
            autofillRequestedExtra = intent.getBooleanExtra(NCPAutofillService.AUTOFILL_REQUEST, false),
            trustedAutofillSelection = AutoLoginActivity.isTrustedAutofillSelectionIntent(this, intent),
            hasAssistStructure = autofillAssistStructure != null,
            webDomain = intent.getStringExtra(AutoLoginActivity.EXTRA_AUTOFILL_WEB_DOMAIN)
        )
        val autofillRequested = autofillSelectionState.autofillRequested
        Timber.d("autofillRequested=$autofillRequested")

        val autofillSearchQuery = autofillSearchQuery(autofillRequested)
        val replyAutofill = createAutofillReply(
            canReplyAutofill = autofillSelectionState.canReplyAutofill,
            autofillAssistStructure = autofillAssistStructure
        )
        val manualWebAutofillWebsite = manualWebAutofillWebsite(autofillSelectionState)
        val backend = NCPApplication.passwordBackend(this) as? NCPPasswordBackend
        observeSsoReauthenticationRequired(passwordsViewModel)
        observeAppPasswordRequired(passwordsViewModel) { intent ->
            if (!SsoAccount.hasValidAccountManagerSignature(this)) {
                Timber.e("AccountManager signature mismatch; refusing app-password launch")
                passwordsViewModel.onAppPasswordFlowUnavailable()
                Toast.makeText(this, R.string.two_factor_app_password_unavailable, Toast.LENGTH_LONG).show()
                return@observeAppPasswordRequired
            }
            try {
                appPasswordLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Timber.e(e, "AccountManager app-password activity unavailable")
                passwordsViewModel.onAppPasswordFlowUnavailable()
                Toast.makeText(this, R.string.two_factor_app_password_unavailable, Toast.LENGTH_LONG).show()
            }
        }

        passwordsViewModel.murenaSyncDisabled.observe(this) { disabled ->
            if (disabled) {
                Toast.makeText(this, R.string.error_sso_sync_disabled, Toast.LENGTH_LONG).show()
                val account = SsoAccount.getCurrentMurenaAccount(this)
                if (account != null && syncDisabled.launch(account)) {
                    passwordsViewModel.onMurenaSyncDisabledShown()
                } else {
                    passwordsViewModel.onMurenaSyncDisabledShown()
                    finish()
                }
            }
        }

        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .diskCache {
                    DiskCache.Builder()
                        .directory(this.cacheDir.resolve("image_cache"))
                        .build()
                }.build()
        }

        enableEdgeToEdge()

        migrationFlowEnabled = !autofillRequested

        setContent {
            val showLockedAccountDialog by passwordsViewModel.clientDeauthorized.observeAsState(false)
            val showE2eeMigrationDialog by passwordsViewModel.showE2eeMigrationDialog.collectAsState()
            NCPAppLockWrapper {
                NextcloudPasswordsApp(
                    passwordsViewModel = passwordsViewModel,
                    onLogOut = { logOut() },
                    onCancelMasterPasswordDialog = ::finish,
                    showLockedAccountDialog = showLockedAccountDialog,
                    onUnlockLockedAccount = { unlockAccountInWeb() },
                    onCancelLockedAccount = ::finish,
                    showE2eeMigrationDialog = migrationFlowEnabled && showE2eeMigrationDialog,
                    onStartE2eeMigration = { launchE2eeMigration(passwordsViewModel) },
                    onCancelE2eeMigration = { finish() },
                    replyAutofill = replyAutofill,
                    manualWebAutofillWebsite = manualWebAutofillWebsite,
                    onManualWebAutofillSave = { password ->
                        if (backend == null || manualWebAutofillWebsite == null) {
                            PasswordSaveResult.Failed("Manual autofill save is unavailable")
                        } else {
                            backend.linkWebsiteForManualSelection(
                                password = password,
                                website = manualWebAutofillWebsite
                            )
                        }
                    },
                    isAutofillRequest = autofillRequested,
                    defaultSearchQuery = autofillSearchQuery
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (syncDisabled.onResume()) return
        if (waitingForUnlockInWeb) {
            waitingForUnlockInWeb = false
            passwordsViewModel.sync()
            return
        }
        if (migrationFlowEnabled) {
            passwordsViewModel.onAppResumedAfterMigration()
        }
    }

    override fun onPause() {
        super.onPause()
        syncDisabled.onPause()
    }

    private fun autofillSearchQuery(autofillRequested: Boolean): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && autofillRequested) {
            intent.getStringExtra(NCPAutofillService.AUTOFILL_SEARCH_HINT) ?: ""
        } else {
            ""
        }
    }

    private fun createAutofillReply(
        canReplyAutofill: Boolean,
        autofillAssistStructure: AssistStructure?
    ): ((String, String, String) -> Unit)? {
        val assistStructure = autofillAssistStructure ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canReplyAutofill) {
            { label, username, password ->
                autofillReply(Triple(label, username, password), assistStructure)
            }
        } else {
            null
        }
    }

    private fun manualWebAutofillWebsite(
        autofillSelectionState: AutofillSelectionState
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            autofillSelectionState.manualWebAutofillWebsite
        } else {
            null
        }
    }

    private fun logOut() {
        if (OkHttpRequestInterface.getInstance() is SsoOkHttpRequest) {
            // logout is unsupported from client app (password app in this case) when using murena account
            startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
            return
        }
        val logOutJob = SupervisorJob()
        val logOutScope = CoroutineScope(Dispatchers.IO + logOutJob)
        logOutScope.launch {
            ApiController.getInstance(this@MainActivity).closeSession()
            UserController.getInstance(this@MainActivity).logOut()
            triggerRebirth()
        }
    }

    private fun unlockAccountInWeb() {
        val passwordsWebUri = passwordsViewModel.preparePasswordsWebUri()

        if (passwordsWebUri != null) {
            runCatching {
                CustomTabsIntent.Builder()
                    .build()
                    .launchUrl(this, passwordsWebUri)
                waitingForUnlockInWeb = true
                passwordsViewModel.clearClientDeauthorized()
            }.onFailure { exception ->
                Timber.e(exception, "Failed to launch Murena Passwords web app for unlock flow")
            }
        }
    }

    private fun triggerRebirth() {
        val mainIntent = Intent.makeRestartActivityTask(
            ComponentName(this, MainActivity::class.java)
        )
        startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun login() {
        val intent = Intent("com.hegocre.nextcloudpasswords.action.login")
            .setPackage(packageName)
        startActivity(intent)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun autofillReply(
        password: Triple<String, String, String>,
        structure: AssistStructure
    ) {
        val dataset = AutofillHelper.buildDataset(this, password, structure, null)

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        }

        setResult(RESULT_OK, replyIntent)

        finish()
    }

}

internal data class AutofillSelectionState(
    val autofillRequested: Boolean,
    val canReplyAutofill: Boolean,
    val manualWebAutofillWebsite: String?
)

internal fun resolveAutofillSelectionState(
    autofillRequestedExtra: Boolean,
    trustedAutofillSelection: Boolean,
    hasAssistStructure: Boolean,
    webDomain: String?
): AutofillSelectionState {
    val autofillRequested = autofillRequestedExtra && trustedAutofillSelection
    return AutofillSelectionState(
        autofillRequested = autofillRequested,
        canReplyAutofill = autofillRequested && hasAssistStructure,
        manualWebAutofillWebsite = if (autofillRequested) {
            NCPAutofillMetadata.normalizeWebsite(webDomain)
        } else {
            null
        }
    )
}
