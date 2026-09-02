package com.hegocre.nextcloudpasswords.ui.activities

import android.app.Activity
import androidx.browser.customtabs.CustomTabsIntent
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import timber.log.Timber

/**
 * Opens the Murena Passwords web app so the user can lift the client deauthorization.
 *
 * @return whether the web app was launched.
 */
fun Activity.openPasswordsWebUnlock(passwordsViewModel: PasswordsViewModel): Boolean {
    val passwordsWebUri = passwordsViewModel.preparePasswordsWebUri() ?: return false

    return runCatching {
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(this, passwordsWebUri)
        passwordsViewModel.clearClientDeauthorized()
    }.onFailure { exception ->
        Timber.e(exception, "Failed to launch Murena Passwords web app for unlock flow")
    }.isSuccess
}
