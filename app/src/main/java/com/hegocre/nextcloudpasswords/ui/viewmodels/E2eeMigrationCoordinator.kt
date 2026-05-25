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
package com.hegocre.nextcloudpasswords.ui.viewmodels

import android.net.Uri
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

class E2eeMigrationCoordinator(
    viewModelScope: CoroutineScope,
    endToEndEncryptionEnabled: StateFlow<Boolean?>,
    private val serverUrlProvider: () -> String?,
    private val migrationSupported: StateFlow<Boolean> = DEFAULT_MIGRATION_SUPPORTED,
    private val migrationEligible: StateFlow<Boolean> = DEFAULT_MIGRATION_ELIGIBLE,
) {
    private val awaitingMigration = MutableStateFlow(false)

    val showMigrationDialog: StateFlow<Boolean> = combine(
        migrationSupported,
        migrationEligible,
        endToEndEncryptionEnabled,
        awaitingMigration
    ) { supported, eligible, enabled, awaiting ->
        supported && eligible && enabled == false && !awaiting
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun preparePasswordsWebUri(): Uri? {
        val baseUrl = serverUrlProvider()
        val expectedHost = baseUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        val candidate = baseUrl?.trimEnd('/')?.plus(PASSWORDS_WEB_PATH)
        val uri = candidate?.let { runCatching { Uri.parse(it) }.getOrNull() }

        return when {
            baseUrl == null || expectedHost == null -> {
                Timber.e("No server URL available; cannot open Murena Passwords web app")
                null
            }

            uri == null -> {
                Timber.e("Could not parse Murena Passwords web URL: %s", candidate)
                null
            }

            !URLUtil.isHttpsUrl(uri.toString()) -> {
                Timber.e("Refusing to launch Murena Passwords web app over insecure URL: %s", uri)
                null
            }

            !uri.host.equals(expectedHost, ignoreCase = true) -> {
                Timber.e(
                    "Refusing to launch Murena Passwords web app: host mismatch (expected=%s actual=%s)",
                    expectedHost,
                    uri.host
                )
                null
            }

            else -> uri
        }
    }

    fun onMigrationLaunched() {
        awaitingMigration.value = true
    }

    fun onMigrationLaunchFailed() {
        awaitingMigration.value = false
    }

    /**
     * Keep [awaitingMigration] true until the caller finishes its refresh work.
     * Otherwise the dialog can briefly reappear while the app is still working
     * with stale `endToEndEncryptionEnabled=false` state from before the return.
     */
    suspend fun onAppResumedAfterMigration(onMigrationReturn: suspend () -> Unit) {
        if (!awaitingMigration.value) return

        try {
            onMigrationReturn()
        } finally {
            awaitingMigration.value = false
        }
    }

    companion object {
        private const val PASSWORDS_WEB_PATH = "/index.php/apps/passwords/"
        private val DEFAULT_MIGRATION_SUPPORTED = MutableStateFlow(true).asStateFlow()
        private val DEFAULT_MIGRATION_ELIGIBLE = MutableStateFlow(true).asStateFlow()
    }
}
