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

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.Presentations
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.ui.components.SuggestPasswordBottomSheet
import com.hegocre.nextcloudpasswords.utils.copyToClipboard
import foundation.e.autofill.GeneratePasswordResult
import foundation.e.autofill.SUGGEST_PASSWORD_DATASET_ID
import kotlinx.coroutines.launch
import timber.log.Timber

class NCPSuggestPasswordActivity : ComponentActivity() {

    private var uiState by mutableStateOf(SuggestPasswordUiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getIntExtra(EXTRA_MODE, MODE_UNKNOWN)
        val passwordIds = IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_PASSWORD_IDS,
            AutofillId::class.java
        ).orEmpty()

        if (mode == MODE_UNKNOWN || (mode == MODE_AUTOFILL && passwordIds.isEmpty())) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            SuggestPasswordBottomSheet(
                isLoading = uiState.isLoading,
                errorMessageRes = uiState.errorMessageRes,
                generatedPassword = uiState.generatedPassword,
                isCredentialManager = mode == MODE_CREDENTIAL_MANAGER,
                onUse = { password -> returnAutofillResult(password, passwordIds) },
                onCopy = { password ->
                    copyToClipboard(password, isSensitive = true)
                    if (mode == MODE_AUTOFILL) {
                        setResult(RESULT_CANCELED)
                    }
                    finish()
                },

                onDismiss = {
                    setResult(RESULT_CANCELED)
                    finish()
                }
            )
        }

        lifecycleScope.launch {
            uiState = runCatching {
                NCPApplication.passwordBackend(this@NCPSuggestPasswordActivity).generatePassword()
            }.getOrElse { error ->
                Timber.e(error, "Failed to generate suggest-password value")
                GeneratePasswordResult.Failed
            }.toUiState()
        }
    }

    private fun returnAutofillResult(password: String, passwordIds: List<AutofillId>) {
        val dataset = buildAutofillDataset(password, passwordIds)
        setResult(
            RESULT_OK,
            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        )
        finish()
    }

    private fun buildAutofillDataset(password: String, passwordIds: List<AutofillId>): Dataset {
        val label = getString(R.string.suggested_password_autofill)
        val presentation = simplePresentation(label)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Dataset.Builder(
                Presentations.Builder()
                    .setMenuPresentation(presentation)
                    .setDialogPresentation(presentation)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            Dataset.Builder(presentation)
        }

        passwordIds.forEach { autofillId ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setField(
                    autofillId,
                    Field.Builder()
                        .setValue(AutofillValue.forText(password))
                        .setPresentations(
                            Presentations.Builder()
                                .setMenuPresentation(presentation)
                                .setDialogPresentation(presentation)
                                .build()
                        )
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setValue(autofillId, AutofillValue.forText(password), presentation)
            }
        }

        return builder
            .setId(SUGGEST_PASSWORD_DATASET_ID)
            .build()
    }

    private fun simplePresentation(label: String): RemoteViews {
        return RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, label)
        }
    }

    companion object {
        private const val EXTRA_MODE = "foundation.e.passwords.autofill.EXTRA_MODE"
        private const val EXTRA_PASSWORD_IDS = "foundation.e.passwords.autofill.EXTRA_PASSWORD_IDS"

        private const val MODE_UNKNOWN = 0
        private const val MODE_AUTOFILL = 1
        private const val MODE_CREDENTIAL_MANAGER = 2

        fun autofillIntent(
            context: Context,
            passwordIds: List<AutofillId>
        ): Intent {
            return Intent(context, NCPSuggestPasswordActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_AUTOFILL)
                .putParcelableArrayListExtra(EXTRA_PASSWORD_IDS, ArrayList(passwordIds))
        }

        fun credentialManagerIntent(context: Context): Intent {
            return Intent(context, NCPSuggestPasswordActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_CREDENTIAL_MANAGER)
        }
    }
}
