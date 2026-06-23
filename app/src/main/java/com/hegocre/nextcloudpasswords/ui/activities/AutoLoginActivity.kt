/*
 * Copyright (C) 2026 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hegocre.nextcloudpasswords.ui.activities

import android.accounts.Account
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillPendingSaveContinuation
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillService
import com.hegocre.nextcloudpasswords.services.autofill.clearAutofillSaveIfAbandoned
import com.hegocre.nextcloudpasswords.services.autofill.resumeAutofillSave
import com.hegocre.nextcloudpasswords.utils.ActionsConst
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import java.util.UUID

class AutoLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseAutoLogin.start(
            forceSsoReauthentication = intent.getBooleanExtra(
                EXTRA_FORCE_SSO_REAUTHENTICATION,
                false
            )
        )
    }

    private val saveContinuationToken: String? by lazy {
        intent.getStringExtra(NCPAutofillPendingSaveContinuation.EXTRA_TOKEN)
    }
    private var continuingFlow = false

    private fun startPostLogin() {
        val token = saveContinuationToken
        if (token != null && NCPAutofillPendingSaveContinuation.matches(token)) {
            continuingFlow = true
            resumeAutofillSave(token)
        } else {
            startMain()
        }
    }

    override fun onDestroy() {
        clearAutofillSaveIfAbandoned(saveContinuationToken, continuingFlow)
        super.onDestroy()
    }

    private fun startMain() {
        val mainScreen = postLoginIntent()
        startActivity(mainScreen)
        finish()
    }

    private fun postLoginIntent(): Intent {
        return if (intent.isTrustedAutofillSelectionIntent(this)) {
            autofillSelectionMainIntent(this, intent)
        } else {
            Intent(ActionsConst.MAIN_SCREEN).setPackage(packageName)
        }
    }

    private val syncDisabled: MurenaSyncDisabledFlow by lazy {
        MurenaSyncDisabledFlow(
            launchSettings = { account -> MurenaAccountSyncSettings.open(this, account) },
            onSyncEnabled = {
                baseAutoLogin.start(
                    forceSsoReauthentication = intent.getBooleanExtra(
                        EXTRA_FORCE_SSO_REAUTHENTICATION,
                        false
                    )
                )
            },
            onStillDisabledOrLaunchFailed = ::finish,
        )
    }

    private val baseAutoLogin: BaseAutoLogin by lazy {
        object : BaseAutoLogin(this@AutoLoginActivity) {
            override fun accountExist() {
                startPostLogin()
            }

            override fun onLoginSuccess() {
                startPostLogin()
            }

            override fun signatureError() {
                openClassicLogin(R.string.error_sso_app_signature)
            }

            override fun accountUnavailable() {
                openClassicLogin(R.string.error_sso_no_account_found)
            }

            override fun syncDisabled(account: Account) {
                Toast.makeText(this@AutoLoginActivity, R.string.error_sso_sync_disabled, Toast.LENGTH_LONG).show()
                if (syncDisabled.launch(account)) {
                    finish()
                }
            }

            override fun ssoFailed() {
                openClassicLogin(R.string.error_sso_failed)
            }

        }
    }

    override fun onResume() {
        super.onResume()
        syncDisabled.onResume()
    }

    override fun onPause() {
        super.onPause()
        syncDisabled.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        baseAutoLogin.onActivityResult(requestCode, resultCode, data)
    }

    private fun openClassicLogin(@StringRes error: Int = R.string.error_sso_unavailable_generic) {
        OkHttpRequestInterface.useBasic(null)
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()

        continuingFlow = true
        startActivity(
            Intent(ActionsConst.CLASSIC_LOGIN).setPackage(packageName).apply {
                saveContinuationToken?.let {
                    putExtra(NCPAutofillPendingSaveContinuation.EXTRA_TOKEN, it)
                }
            }
        )

        finish()
    }

    companion object {
        private const val EXTRA_POST_LOGIN_DESTINATION =
            "com.hegocre.nextcloudpasswords.extra.POST_LOGIN_DESTINATION"
        private const val EXTRA_AUTOFILL_SEARCH_HINT =
            "com.hegocre.nextcloudpasswords.extra.AUTOFILL_SEARCH_HINT"
        const val EXTRA_AUTOFILL_WEB_DOMAIN =
            "com.hegocre.nextcloudpasswords.extra.AUTOFILL_WEB_DOMAIN"
        private const val EXTRA_AUTOFILL_ASSIST_STRUCTURE =
            "com.hegocre.nextcloudpasswords.extra.AUTOFILL_ASSIST_STRUCTURE"
        private const val EXTRA_AUTOFILL_CONTINUATION_TOKEN =
            "com.hegocre.nextcloudpasswords.extra.AUTOFILL_CONTINUATION_TOKEN"
        private const val EXTRA_FORCE_SSO_REAUTHENTICATION =
            "com.hegocre.nextcloudpasswords.extra.FORCE_SSO_REAUTHENTICATION"

        private const val DESTINATION_AUTOFILL_SELECTION = "autofill_selection"
        private const val AUTOFILL_CONTINUATION_PREFS =
            "com.hegocre.nextcloudpasswords.autofill_continuation"
        private const val PREF_AUTOFILL_CONTINUATION_TOKEN = "autofill_continuation_token"

        fun intent(context: Context, sourceIntent: Intent? = null): Intent {
            return Intent(context, AutoLoginActivity::class.java).apply {
                if (
                    sourceIntent?.isAutofillSelectionRequest(context) == true ||
                    sourceIntent?.isTrustedAutofillSelectionIntent(context) == true
                ) {
                    putAutofillSelectionExtras(sourceIntent)
                    addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                }
            }
        }

        fun reauthenticationIntent(context: Context, sourceIntent: Intent? = null): Intent =
            intent(context, sourceIntent).putExtra(EXTRA_FORCE_SSO_REAUTHENTICATION, true)

        fun autofillSelectionIntent(
            context: Context,
            searchHint: String,
            webDomain: String?
        ): Intent {
            val token = newAutofillContinuationToken(context)
            return Intent(context, AutoLoginActivity::class.java).apply {
                putExtra(EXTRA_POST_LOGIN_DESTINATION, DESTINATION_AUTOFILL_SELECTION)
                putExtra(
                    EXTRA_AUTOFILL_SEARCH_HINT,
                    searchHint.take(MAX_AUTOFILL_SEARCH_HINT_LENGTH)
                )
                putExtra(EXTRA_AUTOFILL_WEB_DOMAIN, webDomain)
                putExtra(EXTRA_AUTOFILL_CONTINUATION_TOKEN, token)
            }
        }

        fun autofillSelectionMainIntent(context: Context, sourceIntent: Intent): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_POST_LOGIN_DESTINATION, DESTINATION_AUTOFILL_SELECTION)
                putExtra(NCPAutofillService.AUTOFILL_REQUEST, true)
                putExtra(
                    NCPAutofillService.AUTOFILL_SEARCH_HINT,
                    sourceIntent.getSanitizedAutofillSearchHint()
                )
                putExtra(
                    EXTRA_AUTOFILL_CONTINUATION_TOKEN,
                    sourceIntent.getStringExtra(EXTRA_AUTOFILL_CONTINUATION_TOKEN)
                )
                putExtra(
                    EXTRA_AUTOFILL_WEB_DOMAIN,
                    sourceIntent.getStringExtra(EXTRA_AUTOFILL_WEB_DOMAIN)
                )
                sourceIntent.getTrustedOrAutofillAssistStructure()?.let { assistStructure ->
                    putExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, assistStructure)
                }
                addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            }
        }

        private fun Intent.putAutofillSelectionExtras(sourceIntent: Intent) {
            putExtra(EXTRA_POST_LOGIN_DESTINATION, DESTINATION_AUTOFILL_SELECTION)
            putExtra(
                EXTRA_AUTOFILL_SEARCH_HINT,
                sourceIntent.getSanitizedAutofillSearchHint()
            )
            putExtra(
                EXTRA_AUTOFILL_CONTINUATION_TOKEN,
                sourceIntent.getStringExtra(EXTRA_AUTOFILL_CONTINUATION_TOKEN)
            )
            putExtra(
                EXTRA_AUTOFILL_WEB_DOMAIN,
                sourceIntent.getStringExtra(EXTRA_AUTOFILL_WEB_DOMAIN)
            )
            sourceIntent.getTrustedOrAutofillAssistStructure()?.let { assistStructure ->
                putExtra(EXTRA_AUTOFILL_ASSIST_STRUCTURE, assistStructure)
            }
        }

        fun isTrustedAutofillSelectionIntent(context: Context, intent: Intent): Boolean {
            return intent.isTrustedAutofillSelectionIntent(context)
        }

        private fun Intent.isAutofillSelectionRequest(context: Context): Boolean {
            return getBooleanExtra(NCPAutofillService.AUTOFILL_REQUEST, false) &&
                getAutofillAssistStructure() != null &&
                hasValidAutofillContinuationToken(context)
        }

        private fun Intent.isTrustedAutofillSelectionIntent(context: Context): Boolean {
            return getStringExtra(EXTRA_POST_LOGIN_DESTINATION) == DESTINATION_AUTOFILL_SELECTION &&
                hasValidAutofillContinuationToken(context)
        }

        private fun Intent.hasValidAutofillContinuationToken(context: Context): Boolean {
            val expectedToken = context.autofillContinuationPrefs()
                .getString(PREF_AUTOFILL_CONTINUATION_TOKEN, null)
            val token = getStringExtra(EXTRA_AUTOFILL_CONTINUATION_TOKEN)
            return !token.isNullOrBlank() && token == expectedToken
        }

        private fun newAutofillContinuationToken(context: Context): String {
            return UUID.randomUUID().toString().also { token ->
                context.autofillContinuationPrefs()
                    .edit()
                    .putString(PREF_AUTOFILL_CONTINUATION_TOKEN, token)
                    .apply()
            }
        }

        private fun Context.autofillContinuationPrefs() = getSharedPreferences(
            AUTOFILL_CONTINUATION_PREFS,
            Context.MODE_PRIVATE
        )

        private fun Intent.getAutofillAssistStructure(): AssistStructure? {
            return IntentCompat.getParcelableExtra(
                this,
                AutofillManager.EXTRA_ASSIST_STRUCTURE,
                AssistStructure::class.java
            )
        }

        private fun Intent.getTrustedAssistStructure(): AssistStructure? {
            return IntentCompat.getParcelableExtra(
                this,
                EXTRA_AUTOFILL_ASSIST_STRUCTURE,
                AssistStructure::class.java
            )
        }

        private fun Intent.getTrustedOrAutofillAssistStructure(): AssistStructure? {
            return getTrustedAssistStructure() ?: getAutofillAssistStructure()
        }

        private fun Intent.getSanitizedAutofillSearchHint(): String {
            return (getStringExtra(EXTRA_AUTOFILL_SEARCH_HINT)
                ?: getStringExtra(NCPAutofillService.AUTOFILL_SEARCH_HINT))
                ?.take(MAX_AUTOFILL_SEARCH_HINT_LENGTH)
                .orEmpty()
        }

        private const val MAX_AUTOFILL_SEARCH_HINT_LENGTH = 256
    }
}
