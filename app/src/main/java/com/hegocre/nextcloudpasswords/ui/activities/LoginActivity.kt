package com.hegocre.nextcloudpasswords.ui.activities

import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillPendingSaveContinuation
import com.hegocre.nextcloudpasswords.services.autofill.clearAutofillSaveIfAbandoned
import com.hegocre.nextcloudpasswords.services.autofill.resumeAutofillSave
import com.hegocre.nextcloudpasswords.ui.components.NCPLoginScreen
import com.hegocre.nextcloudpasswords.utils.ActionsConst
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class LoginActivity : ComponentActivity() {

    private val saveContinuationToken: String? by lazy {
        intent.getStringExtra(NCPAutofillPendingSaveContinuation.EXTRA_TOKEN)
    }
    private var continuingFlow = false

    private val addMurenaAccountLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            continuingFlow = true
            startActivity(
                Intent(this, AutoLoginActivity::class.java).apply {
                    saveContinuationToken?.let {
                        putExtra(NCPAutofillPendingSaveContinuation.EXTRA_TOKEN, it)
                    }
                }
            )
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val loginIntent = Intent(this, WebLoginActivity::class.java)

        setContent {
            NCPLoginScreen(
                loginIntent = loginIntent,
                onMurenaWorkspaceClick = { launchMurenaWorkspaceLogin() },
                onLoginSuccess = {
                    proceedAfterLogin()
                },
                onLoginFailed = {
                    PreferencesManager.getInstance(this).setSkipCertificateValidation(false)
                    Toast.makeText(this, getString(R.string.error_logging_in), Toast.LENGTH_LONG)
                        .show()
                }
            )
        }
    }

    private fun proceedAfterLogin() {
        continuingFlow = true
        val token = saveContinuationToken
        if (token != null && NCPAutofillPendingSaveContinuation.matches(token)) {
            resumeAutofillSave(token)
        } else {
            startActivity(Intent(ActionsConst.MAIN_SCREEN).setPackage(packageName))
            finish()
        }
    }

    override fun onDestroy() {
        clearAutofillSaveIfAbandoned(saveContinuationToken, continuingFlow)
        super.onDestroy()
    }

    private fun launchMurenaWorkspaceLogin() {
        lifecycleScope.launch {
            val addAccountIntent = getAddAccountIntent()
            addAccountIntent?.let(addMurenaAccountLauncher::launch)
        }
    }

    private suspend fun getAddAccountIntent(): Intent? = try {
        val bundle = withContext(Dispatchers.IO) {
            AccountManager.get(this@LoginActivity).addAccount(
                SsoAccount.MURENA_ACCOUNT_TYPE,
                OAUTH2_ACCESS_TOKEN_TYPE,
                null,
                null,
                null,
                null,
                null,
            ).getResult()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle?.getParcelable(AccountManager.KEY_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle?.getParcelable(AccountManager.KEY_INTENT)
        }
    } catch (e: SecurityException) {
        Timber.tag(LOGIN_ACTIVITY_TAG).e(e, "Missing permission to start addAccount() flow")
        showAddAccountFailure()
        null
    } catch (e: OperationCanceledException) {
        Timber.tag(LOGIN_ACTIVITY_TAG).i(e, "Add account flow was cancelled")
        showAddAccountFailure()
        null
    } catch (e: AuthenticatorException) {
        Timber.tag(LOGIN_ACTIVITY_TAG).e(e, "Authenticator failed to create account")
        showAddAccountFailure()
        null
    } catch (e: IOException) {
        Timber.tag(LOGIN_ACTIVITY_TAG).e(e, "Failed to get add account intent")
        showAddAccountFailure()
        null
    }

    private suspend fun showAddAccountFailure() {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@LoginActivity, R.string.error_sso_unavailable_generic, Toast.LENGTH_LONG)
                .show()
        }
    }

    companion object {
        private const val LOGIN_ACTIVITY_TAG = "LoginActivity"
        private const val OAUTH2_ACCESS_TOKEN_TYPE = "oauth2-access-token"
    }
}
