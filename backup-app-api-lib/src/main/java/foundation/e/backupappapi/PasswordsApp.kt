/*
 * Copyright (c) 2026 e Foundation
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
 *
 */

package foundation.e.backupappapi

import android.content.ComponentName
import android.content.Intent
import foundation.e.data.SetupConsent

object PasswordsApp {
    const val PASSWORD_SYNC_AUTHORITY = "foundation.e.passwords.providers.PasswordSyncProvider"
    const val EXTRA_IS_RESTORE = "is_restore"
    internal const val BACKUP_API_ACTION = "foundation.e.passwords.ACTION_BACKUP_APP_PRIVATE_API"
    private const val PASSWORDS_APP_PACKAGE = "foundation.e.passwords"
    private const val SETUP_ACTIVITY =
        "com.hegocre.nextcloudpasswords.ui.activities.BackupAppSetupActivity"
    private const val BACKUP_API_SERVICE =
        "com.hegocre.nextcloudpasswords.backupApp.BackupAppService"
    private const val LAUNCH_ACTIVITY =
        "com.hegocre.nextcloudpasswords.ui.activities.AutoLoginActivityAlias"

    fun backupApiServiceComponent() =
        ComponentName(PASSWORDS_APP_PACKAGE, BACKUP_API_SERVICE)

    fun newE2eeSetupIntent(isRestore: Boolean = false) =
        Intent(SetupConsent.SETUP_ACTION).apply {
            component = ComponentName(PASSWORDS_APP_PACKAGE, SETUP_ACTIVITY)
            putExtra(EXTRA_IS_RESTORE, isRestore)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

    fun passwordAppIntent() = Intent().setClassName(PASSWORDS_APP_PACKAGE, LAUNCH_ACTIVITY)
}
