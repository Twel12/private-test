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
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillSaveInteractionActivity
import foundation.e.autofill.PasswordRequestSource
import foundation.e.autofill.PasswordSaveRequest
import timber.log.Timber

class SaveCredentialActivity : ComponentActivity() {

    private val saveLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        setResult(result.resultCode)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The save flow is already running and will deliver through saveLauncher.
        if (savedInstanceState != null) return

        val request = intent?.let(::saveRequestOrNull) ?: run {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        saveLauncher.launch(NCPAutofillSaveInteractionActivity.intent(this, request))
    }

    private fun saveRequestOrNull(intent: Intent): PasswordSaveRequest? {
        val callerPackage = callingPackage?.takeIf { it.isNotBlank() }
        val password = intent.getStringExtra(EXTRA_PASSWORD)?.takeIf { it.isNotBlank() }

        if (callerPackage == null || password == null) {
            Timber.w(
                "credential save rejected: startedForResult=%s passwordPresent=%s",
                callerPackage != null,
                password != null
            )
            return null
        }

        val username = intent.getStringExtra(EXTRA_USERNAME)?.takeIf { it.isNotBlank() }
        val identityKey = intent.getStringExtra(EXTRA_IDENTITY_KEY)?.takeIf { it.isNotBlank() }
        if (username == null && identityKey == null) {
            Timber.w("credential save from %s has no identity; saves will duplicate", callerPackage)
        }

        return PasswordSaveRequest(
            source = PasswordRequestSource.EXTERNAL_APP,
            packageName = callerPackage,
            webDomain = null,
            origin = null,
            username = username,
            password = password,
            identityKey = identityKey
        )
    }

    companion object {
        const val EXTRA_PASSWORD = "foundation.e.passwords.extra.PASSWORD"
        const val EXTRA_USERNAME = "foundation.e.passwords.extra.USERNAME"
        const val EXTRA_IDENTITY_KEY = "foundation.e.passwords.extra.IDENTITY_KEY"
    }
}
