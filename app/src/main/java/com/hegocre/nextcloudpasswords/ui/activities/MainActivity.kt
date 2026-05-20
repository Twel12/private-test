package com.hegocre.nextcloudpasswords.ui.activities

import android.app.assist.AssistStructure
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.services.autofill.AutofillHelper
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillService
import com.hegocre.nextcloudpasswords.ui.components.NCPAppLockWrapper
import com.hegocre.nextcloudpasswords.ui.components.NextcloudPasswordsApp
import com.hegocre.nextcloudpasswords.ui.migration.launchE2eeMigration
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import com.hegocre.nextcloudpasswords.utils.LogHelper
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import com.hegocre.nextcloudpasswords.utils.SsoOkHttpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : FragmentActivity() {

    private val passwordsViewModel by viewModels<PasswordsViewModel>()

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

        val autofillRequested =
            intent.getBooleanExtra(NCPAutofillService.AUTOFILL_REQUEST, false) &&
                autofillAssistStructure != null &&
                AutoLoginActivity.isTrustedAutofillSelectionIntent(this, intent)
        Timber.d("autofillRequested=$autofillRequested")

        val autofillSearchQuery =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && autofillRequested) {
                intent.getStringExtra(NCPAutofillService.AUTOFILL_SEARCH_HINT) ?: ""
            } else {
                ""
            }

        val replyAutofill: ((String, String, String) -> Unit)? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && autofillRequested
            ) {
                { label, username, password ->
                    autofillReply(Triple(label, username, password), autofillAssistStructure)
                }
            } else null
        observeSsoReauthenticationRequired(passwordsViewModel)

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
                    showLockedAccountDialog = showLockedAccountDialog,
                    onUnlockLockedAccount = { unlockAccountInWeb() },
                    onCancelLockedAccount = ::finish,
                    showE2eeMigrationDialog = migrationFlowEnabled && showE2eeMigrationDialog,
                    onStartE2eeMigration = { launchE2eeMigration(passwordsViewModel) },
                    onCancelE2eeMigration = { finish() },
                    replyAutofill = replyAutofill,
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
        val passwordsWebUri = passwordsViewModel.prepareE2eeMigrationUri()

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
