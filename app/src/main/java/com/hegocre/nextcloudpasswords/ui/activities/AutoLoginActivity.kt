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
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.data.user.UserController.Companion.getInstance
import com.hegocre.nextcloudpasswords.utils.ActionsConst
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount

class AutoLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getInstance(this).isLoggedIn) {
            startMain()
            return
        }

        if (isSignatureMismatchWithAccountManager()) {
            openClassicLogin(R.string.error_sso_app_signature)
            return
        }

        val murenaAccount = AccountImporter.findAccounts(this)
            .firstOrNull { it != null && it.type == MURENA_ACCOUNT_TYPE }

        if (murenaAccount == null) {
            openClassicLogin(R.string.error_sso_no_account_found)
            return
        }

        if (!murenaAccount.isSyncEnabled()) {
            openClassicLogin(R.string.error_sso_sync_disabled)
            return
        }

        AccountImporter.pickAccount(this, murenaAccount)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this, ::onSsoLoginSuccess)
        } catch (e: AccountImportCancelledException) {
            openClassicLogin(R.string.error_sso_failed)
        }
    }

    private fun Account.isSyncEnabled() =
        ContentResolver.getMasterSyncAutomatically()
            && ContentResolver.getSyncAutomatically(this, CONTENT_AUTHORITY)

    private fun isSignatureMismatchWithAccountManager() = packageManager.checkSignatures(
        ACCOUNT_MANAGER_PACKAGE,
        packageName
    ) != PackageManager.SIGNATURE_MATCH

    private fun onSsoLoginSuccess(ssoAccount: SingleSignOnAccount) {
        SingleAccountHelper.commitCurrentAccount(applicationContext, ssoAccount.name)
        OkHttpRequestInterface.useSso(this, ssoAccount)
        startMain()
    }

    private fun openClassicLogin(@StringRes error: Int = R.string.error_sso_unavailable_generic) {
        OkHttpRequestInterface.useBasic(null)
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()

        startActivity(
            Intent(ActionsConst.CLASSIC_LOGIN).setPackage(packageName)
        )

        finish()
    }

    private fun startMain() {
        val mainScreen = Intent(ActionsConst.MAIN_SCREEN)
            .setPackage(packageName)
        startActivity(mainScreen)
        finish()
    }

    companion object {
        private const val MURENA_ACCOUNT_TYPE = "e.foundation.webdav.eelo"

        private const val ACCOUNT_MANAGER_PACKAGE = "foundation.e.accountmanager"

        private const val CONTENT_AUTHORITY =
            "foundation.e.passwords.providers.PasswordSyncProvider"
    }
}
