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
import com.hegocre.nextcloudpasswords.utils.SsoAccount

class MurenaSyncDisabledFlow(
    private val launchSettings: (Account) -> Boolean,
    private val isSyncEnabled: (Account) -> Boolean = SsoAccount::isMurenaSyncEnabled,
    private val onSyncEnabled: () -> Unit,
    private val onStillDisabledOrLaunchFailed: () -> Unit,
) {

    private var waitingForSyncSettings = false
    private var syncSettingsWasOpened = false
    private var syncSettingsAccount: Account? = null

    fun launch(account: Account): Boolean {
        syncSettingsAccount = account
        syncSettingsWasOpened = false
        waitingForSyncSettings = launchSettings(account)
        if (!waitingForSyncSettings) {
            onStillDisabledOrLaunchFailed()
        }
        return waitingForSyncSettings
    }

    fun onPause() {
        if (waitingForSyncSettings) {
            syncSettingsWasOpened = true
        }
    }

    fun onResume(): Boolean {
        if (!waitingForSyncSettings || !syncSettingsWasOpened) return false

        waitingForSyncSettings = false
        syncSettingsWasOpened = false
        if (syncSettingsAccount?.let(isSyncEnabled) == true) {
            onSyncEnabled()
        } else {
            onStillDisabledOrLaunchFailed()
        }
        return true
    }
}
