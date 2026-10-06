package foundation.e.findmydevice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import foundation.e.findmydevice.R
import foundation.e.findmydevice.util.PasswordsMirror
import foundation.e.passwords.companion.AlreadyExists
import foundation.e.passwords.companion.CompanionCodec
import foundation.e.passwords.companion.CompanionProtocol
import foundation.e.passwords.companion.Failed
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.NeedsUser
import foundation.e.passwords.companion.PasswordsCompanionClient
import foundation.e.passwords.companion.SaveResult
import foundation.e.passwords.companion.Saved
import kotlinx.coroutines.launch

class PasswordsSaver(val busy: Boolean, val save: (String) -> Unit)

@Composable
fun rememberPasswordsSaver(onResult: (SaveResult) -> Unit): PasswordsSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { PasswordsCompanionClient(context) }
    val currentOnResult by rememberUpdatedState(onResult)
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        busy = false
        currentOnResult(CompanionCodec.decodeSave(result.data?.getBundleExtra(CompanionProtocol.EXTRA_RESULT)))
    }
    val save = remember(client, launcher) {
        { code: String ->
            if (!busy) {
                busy = true
                scope.launch {
                    var handedToUser = false
                    try {
                        when (val result = PasswordsMirror.save(context, client, code)) {
                            is NeedsUser -> {
                                launcher.launch(IntentSenderRequest.Builder(result.intent).build())
                                handedToUser = true
                            }
                            else -> currentOnResult(result)
                        }
                    } finally {
                        if (!handedToUser) busy = false
                    }
                }
            }
            Unit
        }
    }
    return PasswordsSaver(busy, save)
}

fun SaveResult.toastMessage(): Int = when (this) {
    is Saved, AlreadyExists -> R.string.save_into_passwords_done
    is Failed -> if (code == FailureCode.UNKNOWN) {
        R.string.save_into_passwords_unavailable
    } else {
        R.string.save_into_passwords_failed
    }
    is NeedsUser -> R.string.save_into_passwords_failed
}
