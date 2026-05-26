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
package com.hegocre.nextcloudpasswords.ui.migration

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import timber.log.Timber

interface E2eeMigrationFlowHandler {
    fun preparePasswordsWebUri(): Uri?

    fun onE2eeMigrationLaunched()

    fun onE2eeMigrationLaunchFailed()

    fun onAppResumedAfterMigration()
}

fun ComponentActivity.launchE2eeMigration(handler: E2eeMigrationFlowHandler) {
    val passwordsWebUri = handler.preparePasswordsWebUri() ?: return
    handler.onE2eeMigrationLaunched()
    runCatching {
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(this, passwordsWebUri)
    }.onFailure { exception ->
        Timber.e(exception, "Failed to launch Murena Passwords web app")
        handler.onE2eeMigrationLaunchFailed()
    }
}
