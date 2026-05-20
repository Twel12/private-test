/*
 * Copyright (C) 2026 e Foundation
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
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import timber.log.Timber

object MurenaAccountSyncSettings {

    fun open(activity: Activity): Boolean {
        val account = SsoAccount.getCurrentMurenaAccount(activity) ?: return false
        return open(activity, account)
    }

    fun open(activity: Activity, account: Account): Boolean {
        val args = Bundle().apply {
            putParcelable(ACCOUNT_KEY, account)
        }

        val intent = Intent(ACCOUNT_SYNC_ACTION).apply {
            setClassName(SETTINGS_PACKAGE, ACCOUNT_SYNC_ACTIVITY)
            putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args)
        }

        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Timber.tag(TAG).e(e, "Activity not found for account sync settings")
            false
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "Permission denied when opening account sync settings")
            false
        }
    }

    private const val TAG = "MurenaAccountSyncSettings"
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val ACCOUNT_SYNC_ACTION = "android.settings.ACCOUNT_SYNC_SETTINGS"
    private const val ACCOUNT_SYNC_ACTIVITY = "com.android.settings.Settings\$AccountSyncSettingsActivity"
    private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
    private const val ACCOUNT_KEY = "account"
}
