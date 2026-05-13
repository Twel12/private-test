package com.hegocre.nextcloudpasswords.ui.activities

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hegocre.nextcloudpasswords.ui.viewmodels.PasswordsViewModel
import kotlinx.coroutines.launch

fun FragmentActivity.observeSsoReauthenticationRequired(
    passwordsViewModel: PasswordsViewModel,
    replayIntent: Intent? = intent,
    beforeReauthentication: () -> Unit = {}
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            passwordsViewModel.ssoReauthRequired.collect { required ->
                if (!required) return@collect

                passwordsViewModel.clearSsoReauthenticationRequired()
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
