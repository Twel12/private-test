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

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.utils.ActionsConst
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface

class AutoLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseAutoLogin.start()
    }

    private fun startMain() {
        val mainScreen = Intent(ActionsConst.MAIN_SCREEN)
            .setPackage(packageName)
        startActivity(mainScreen)
        finish()
    }

    private val baseAutoLogin by lazy {
        object : BaseAutoLogin(this@AutoLoginActivity) {
            override fun accountExist() {
                startMain()
            }

            override fun onLoginSuccess() {
                startMain()
            }

            override fun signatureError() {
                openClassicLogin(R.string.error_sso_app_signature)
            }

            override fun accountUnavailable() {
                openClassicLogin(R.string.error_sso_no_account_found)
            }

            override fun syncDisabled() {
                openClassicLogin(R.string.error_sso_sync_disabled)
            }

            override fun ssoFailed() {
                openClassicLogin(R.string.error_sso_failed)
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        baseAutoLogin.onActivityResult(requestCode, resultCode, data)
    }

    private fun openClassicLogin(@StringRes error: Int = R.string.error_sso_unavailable_generic) {
        OkHttpRequestInterface.useBasic(null)
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()

        startActivity(
            Intent(ActionsConst.CLASSIC_LOGIN).setPackage(packageName)
        )

        finish()
    }
}
