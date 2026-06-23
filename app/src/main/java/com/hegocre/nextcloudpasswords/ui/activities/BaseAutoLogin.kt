package com.hegocre.nextcloudpasswords.ui.activities

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import com.hegocre.nextcloudpasswords.data.user.UserController.Companion.getInstance
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount

abstract class BaseAutoLogin(val activity: Activity) {

    abstract fun accountExist()

    abstract fun onLoginSuccess()

    abstract fun signatureError()

    abstract fun accountUnavailable()

    abstract fun syncDisabled(account: Account)

    abstract fun ssoFailed()

    fun start(forceSsoReauthentication: Boolean = false) {
        val murenaAccount = SsoAccount.getFirstMurenaAccount(activity)
        when {
            !forceSsoReauthentication && getInstance(activity).isLoggedIn -> accountExist()
            isSignatureMismatchWithAccountManager() -> signatureError()
            murenaAccount == null -> accountUnavailable()
            !SsoAccount.isMurenaSyncEnabled(murenaAccount) -> syncDisabled(murenaAccount)
            else -> AccountImporter.pickAccount(activity, murenaAccount)
        }
    }

    private fun isSignatureMismatchWithAccountManager() =
        !SsoAccount.hasValidAccountManagerSignature(activity)

    private fun onSsoLoginSuccess(ssoAccount: SingleSignOnAccount) {
        SingleAccountHelper.commitCurrentAccount(activity.application, ssoAccount.name)
        OkHttpRequestInterface.useSso(activity.application, ssoAccount)
        onLoginSuccess()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, activity, ::onSsoLoginSuccess)
        } catch (_: AccountImportCancelledException) {
            ssoFailed()
        }
    }

}
