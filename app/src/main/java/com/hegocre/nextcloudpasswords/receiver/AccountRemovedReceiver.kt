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
package com.hegocre.nextcloudpasswords.receiver

import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.hegocre.nextcloudpasswords.data.user.UserController
import java.util.Optional

const val ACTION_ACCOUNT_REMOVED: String = "foundation.e.accountmanager.action.ACCOUNT_REMOVED"
const val SUPPORTED_ACCOUNT_TYPE = "e.foundation.webdav.eelo"

class AccountRemovedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (ACTION_ACCOUNT_REMOVED != intent.action) {
            return
        }
        val data = intent.extras ?: return
        if (getAccountName(data).isPresent) {
            UserController.getInstance(context).onMurenaAccountRemoved()
        }
    }

    private fun getAccountName(data: Bundle): Optional<String> {
        if (!isSupportedAccountType(data)) {
            return Optional.empty()
        }
        val accountName = data.getString(AccountManager.KEY_ACCOUNT_NAME)
        if (accountName == null || accountName.trim { it <= ' ' }.isEmpty()) {
            return Optional.empty()
        }
        return Optional.of(accountName)
    }


    private fun isSupportedAccountType(data: Bundle): Boolean {
        val accountTpe = data.getString(AccountManager.KEY_ACCOUNT_TYPE)
        return SUPPORTED_ACCOUNT_TYPE == accountTpe
    }

}

