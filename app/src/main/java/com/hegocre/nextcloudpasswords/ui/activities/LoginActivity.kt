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
import com.hegocre.nextcloudpasswords.ui.components.NCPLoginScreen
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class LoginActivity : ComponentActivity() {

    private val addMurenaAccountLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            startActivity(Intent(this, AutoLoginActivity::class.java))
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
                    val intent = Intent("com.hegocre.nextcloudpasswords.action.main")
                        .setPackage(packageName)
                    startActivity(intent)
                    finish()
                },
                onLoginFailed = {
                    PreferencesManager.getInstance(this).setSkipCertificateValidation(false)
                    Toast.makeText(this, getString(R.string.error_logging_in), Toast.LENGTH_LONG)
                        .show()
                }
            )
        }
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
                MURENA_ACCOUNT_TYPE,
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
        private const val MURENA_ACCOUNT_TYPE = "e.foundation.webdav.eelo"
        private const val OAUTH2_ACCESS_TOKEN_TYPE = "oauth2-access-token"
    }
}
