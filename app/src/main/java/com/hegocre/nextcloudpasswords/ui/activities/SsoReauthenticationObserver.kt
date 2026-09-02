package com.hegocre.nextcloudpasswords.ui.activities

import android.content.Intent
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import kotlinx.coroutines.launch

private const val REAUTHENTICATION_RETRY_WINDOW_MS = 30_000L

private var lastReauthenticationAt: Long? = null

fun FragmentActivity.observeSsoReauthenticationRequired(
    passwordsViewModel: PasswordsViewModel,
    replayIntent: Intent? = intent,
    beforeReauthentication: () -> Unit = {}
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            passwordsViewModel.sessionOpen.collect { open ->
                if (open) lastReauthenticationAt = null
            }
        }
    }

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            passwordsViewModel.ssoReauthRequired.collect { required ->
                if (!required) return@collect

                passwordsViewModel.clearSsoReauthenticationRequired()

                val now = SystemClock.elapsedRealtime()
                val previous = lastReauthenticationAt
                if (previous != null && now - previous < REAUTHENTICATION_RETRY_WINDOW_MS) {
                    passwordsViewModel.reportAccountSyncIssue()
                    return@collect
                }
                lastReauthenticationAt = now

                beforeReauthentication()
                startActivity(
                    AutoLoginActivity.reauthenticationIntent(
                        this@observeSsoReauthenticationRequired,
                        replayIntent
                    )
                )
                finish()
            }
        }
    }
}
