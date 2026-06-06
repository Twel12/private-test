/*
 * Copyright (C) 2026 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.hegocre.nextcloudpasswords.ui.activities

import android.app.AlertDialog
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import com.hegocre.nextcloudpasswords.utils.AppPasswordRequest
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import kotlinx.coroutines.launch

/** Shows an explain-then-launch dialog when [AppPasswordRequest] signals a 2FA-blocked account. */
fun FragmentActivity.observeAppPasswordRequired(
    passwordsViewModel: PasswordsViewModel,
    launchFlow: (Intent) -> Unit
) {
    var dialog: AlertDialog? = null
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                passwordsViewModel.appPasswordRequired.collect { prompt ->
                    if (prompt == null) {
                        dialog?.dismiss()
                        dialog = null
                        return@collect
                    }
                    if (dialog?.isShowing == true || isFinishing || isDestroyed) return@collect

                    dialog = AlertDialog.Builder(this@observeAppPasswordRequired)
                        .setTitle(R.string.two_factor_app_password_title)
                        .setMessage(R.string.two_factor_app_password_message)
                        .setPositiveButton(R.string.two_factor_app_password_enable) { d, _ ->
                            passwordsViewModel.onLaunchingAppPasswordFlow()
                            launchFlow(accountManagerAppPasswordIntent(prompt))
                            d.dismiss()
                        }
                        .setNegativeButton(R.string.two_factor_app_password_not_now) { d, _ ->
                            passwordsViewModel.onAppPasswordFlowDismissed()
                            d.dismiss()
                        }
                        .setOnCancelListener { passwordsViewModel.onAppPasswordFlowDismissed() }
                        .setOnDismissListener { dialog = null }
                        .create()
                        .also { it.show() }
                }
            } finally {
                dialog?.dismiss()
                dialog = null
            }
        }
    }
}

private fun accountManagerAppPasswordIntent(prompt: AppPasswordRequest.Prompt): Intent =
    Intent(ACCOUNT_MANAGER_APP_PASSWORD_ACTION).apply {
        setPackage(SsoAccount.ACCOUNT_MANAGER_PACKAGE)
        putExtra(EXTRA_ACCOUNT_NAME, prompt.accountName)
        putExtra(EXTRA_ACCOUNT_TYPE, prompt.accountType)
    }

private const val ACCOUNT_MANAGER_APP_PASSWORD_ACTION =
    "foundation.e.accountmanager.ui.setup.AppPasswordActivity"
private const val EXTRA_ACCOUNT_NAME = "accountName"
private const val EXTRA_ACCOUNT_TYPE = "accountType"
