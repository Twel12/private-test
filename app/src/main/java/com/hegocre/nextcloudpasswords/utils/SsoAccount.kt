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
package com.hegocre.nextcloudpasswords.utils

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount

object SsoAccount {

    fun getFirstMurenaAccount(context: Context): Account? {
        return findMurenaAccounts(context).firstOrNull()
    }

    fun getCurrentSingleSignOnAccount(context: Context): SingleSignOnAccount? {
        return try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(context)
        } catch (_: NextcloudFilesAppAccountNotFoundException) {
            null
        } catch (_: NoCurrentAccountSelectedException) {
            null
        }
    }

    fun getCurrentMurenaAccount(context: Context): Account? {
        val currentAccount = getCurrentSingleSignOnAccount(context) ?: return null
        return findMurenaAccounts(context)
            .firstOrNull {
                it.name == currentAccount.name
            }
    }

    fun isMurenaSyncEnabled(account: Account): Boolean {
        return ContentResolver.getMasterSyncAutomatically() &&
            ContentResolver.getSyncAutomatically(account, CONTENT_AUTHORITY)
    }

    fun isCurrentMurenaSyncEnabled(context: Context): Boolean {
        val murenaAccount = getCurrentMurenaAccount(context) ?: return true
        return isMurenaSyncEnabled(murenaAccount)
    }

    /** Whether the installed AccountManager is signed with the same certificate as this app. */
    fun hasValidAccountManagerSignature(context: Context): Boolean =
        context.packageManager.checkSignatures(
            ACCOUNT_MANAGER_PACKAGE,
            context.packageName
        ) == PackageManager.SIGNATURE_MATCH

    private fun findMurenaAccounts(context: Context): List<Account> {
        return try {
            AccountImporter.findAccounts(context)
                .filterNotNull()
                .filter { it.type == MURENA_ACCOUNT_TYPE }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    const val MURENA_ACCOUNT_TYPE = "e.foundation.webdav.eelo"
    const val ACCOUNT_MANAGER_PACKAGE = "foundation.e.accountmanager"
    private const val CONTENT_AUTHORITY =
        "foundation.e.passwords.providers.PasswordSyncProvider"

}
