/*
 *  Copyright MURENA SAS 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.hegocre.nextcloudpasswords.services.autofill

import android.content.Intent
import foundation.e.auto_fill.PasswordRequestSource
import foundation.e.auto_fill.PasswordSaveRequest
import timber.log.Timber

object NCPAutofillPendingSaveStore {
    data class PendingSave(
        val request: PasswordSaveRequest,
        val selectedCredentialId: String?,
        val createNew: Boolean
    )

    fun putExtras(
        intent: Intent,
        request: PasswordSaveRequest,
        selectedCredentialId: String?,
        createNew: Boolean
    ): Intent {
        intent
            .putExtra(EXTRA_SOURCE, request.source.name)
            .putExtra(EXTRA_PACKAGE_NAME, request.packageName)
            .putExtra(EXTRA_WEB_DOMAIN, request.webDomain)
            .putExtra(EXTRA_ORIGIN, request.origin)
            .putExtra(EXTRA_USERNAME, request.username)
            .putExtra(EXTRA_PASSWORD, request.password)
            .putExtra(EXTRA_IS_WEB_ORIGIN_REQUEST, request.isWebOriginRequest)
            .putExtra(EXTRA_SELECTED_CREDENTIAL_ID, selectedCredentialId)
            .putExtra(EXTRA_CREATE_NEW, createNew)
        Timber.d(
            "attached pending save package=**, usernamePresent=${request.username?.isNotBlank() == true}, " +
                "selectedPresent=${selectedCredentialId.isNullOrBlank().not()}, createNew=$createNew"
        )
        return intent
    }

    fun fromIntent(intent: Intent): PendingSave? {
        return runCatching {
            Timber.d("fromIntent extrasPresent=${intent.extras != null}")
            val source = intent.getStringExtra(EXTRA_SOURCE)
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { PasswordRequestSource.valueOf(it) }.getOrNull() }
                ?: PasswordRequestSource.AUTOFILL
            val password = intent.getStringExtra(EXTRA_PASSWORD)?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            Timber.d(
                "fromIntent source=$source, package=**, " +
                    "usernamePresent=${intent.getStringExtra(EXTRA_USERNAME)?.isNotBlank() == true}, " +
                    "passwordPresent=true"
            )
            PendingSave(
                request = PasswordSaveRequest(
                    source = source,
                    packageName = intent.getBlankableStringExtra(EXTRA_PACKAGE_NAME),
                    webDomain = intent.getBlankableStringExtra(EXTRA_WEB_DOMAIN),
                    origin = intent.getBlankableStringExtra(EXTRA_ORIGIN),
                    username = intent.getBlankableStringExtra(EXTRA_USERNAME),
                    password = password,
                    isWebOriginRequest = intent.getBooleanExtra(EXTRA_IS_WEB_ORIGIN_REQUEST, false)
                ),
                selectedCredentialId = intent.getBlankableStringExtra(EXTRA_SELECTED_CREDENTIAL_ID),
                createNew = intent.getBooleanExtra(EXTRA_CREATE_NEW, false)
            )
        }.getOrNull().also { pendingSave ->
            Timber.d("read pending save from intent present=${pendingSave != null}")
        }
    }

    private fun Intent.getBlankableStringExtra(key: String): String? {
        return getStringExtra(key)?.takeIf { it.isNotBlank() }
    }

    private const val EXTRA_SOURCE = "foundation.e.passwords.autofill.PENDING_SOURCE"
    private const val EXTRA_PACKAGE_NAME = "foundation.e.passwords.autofill.PENDING_PACKAGE_NAME"
    private const val EXTRA_WEB_DOMAIN = "foundation.e.passwords.autofill.PENDING_WEB_DOMAIN"
    private const val EXTRA_ORIGIN = "foundation.e.passwords.autofill.PENDING_ORIGIN"
    private const val EXTRA_USERNAME = "foundation.e.passwords.autofill.PENDING_USERNAME"
    private const val EXTRA_PASSWORD = "foundation.e.passwords.autofill.PENDING_PASSWORD"
    private const val EXTRA_IS_WEB_ORIGIN_REQUEST =
        "foundation.e.passwords.autofill.PENDING_IS_WEB_ORIGIN_REQUEST"
    private const val EXTRA_SELECTED_CREDENTIAL_ID =
        "foundation.e.passwords.autofill.PENDING_SELECTED_CREDENTIAL_ID"
    private const val EXTRA_CREATE_NEW = "foundation.e.passwords.autofill.PENDING_CREATE_NEW"
}
