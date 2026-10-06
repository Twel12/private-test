package foundation.e.findmydevice.ui

import android.annotation.SuppressLint
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foundation.e.findmydevice.R
import foundation.e.findmydevice.storage.PersistentStorage
import foundation.e.findmydevice.ui.GenerationPasswordScreen.BOX_SIZE
import foundation.e.findmydevice.ui.GenerationPasswordScreen.CORNER_SIZE
import foundation.e.findmydevice.ui.GenerationPasswordScreen.FONT_SIZE
import foundation.e.findmydevice.ui.buttons.actionColor
import foundation.e.findmydevice.ui.buttons.buttonColor
import foundation.e.findmydevice.util.Dimens
import foundation.e.findmydevice.util.PasswordGenerator
import foundation.e.passwords.companion.Saved
import kotlinx.coroutines.launch

/**
 * GenerationPasswordScreen
 *
 * This class implements a screen within the application's user interface that
 * is responsible for generating and displaying a new, random password.
 **/
object GenerationPasswordScreen {

    internal const val CODE_COLOR = 0xFF1A9E24
    internal const val CODE_COLOR_BG = 0x321A9E24
    internal const val TAG = "GenerationPasswordScreen"

    internal val BOX_SIZE = 32.dp
    internal val FONT_SIZE= 24.sp
    internal val CORNER_SIZE = 5.dp

    @SuppressLint("ComposableNaming")
    @Composable
    fun displayScreen(
        onBackPressed: () -> Unit,
        onSelection: () -> Unit,
        onCodeSet: () -> Unit
    ) {
        BackHandler(onBack = { onBackPressed() })
        generatePasswordScreenContent(onSelection, onCodeSet)
    }
}

@Preview(showBackground = true)
@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenPreview() {
    generatePasswordScreenContent(onSelection = {}, onCodeSet = {})
}

@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenContent(onSelection: () -> Unit, onCodeSet: () -> Unit) {
    val context = LocalContext.current
    val persistentStorage = PersistentStorage(context)
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var storedPassword by remember { mutableStateOf("") }
    var showUpdatePrompt by remember { mutableStateOf(false) }
    var isSwitchChecked by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        scope.launch {
            val loaded = persistentStorage.loadCodeState()
            currentPassword = loaded.current
            storedPassword = loaded.stored
            isSwitchChecked = loaded.statusOn
        }
    }

    @Composable
    fun displayCharacter(char: Char) {
        Text(
            text = char.toString(),
            fontWeight = FontWeight.Medium,
            fontSize = FONT_SIZE,
            textAlign = TextAlign.Center,
            color = Color(GenerationPasswordScreen.CODE_COLOR)
        )
    }

    @Composable
    fun displayPassword() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            currentPassword.forEach { char ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CORNER_SIZE))
                        .background(Color(GenerationPasswordScreen.CODE_COLOR_BG))
                        .size(BOX_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    displayCharacter(char = char)
                }
            }
        }
    }

    @Composable
    fun generateNewCode() {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    scope.launch {

                        val newPassword = PasswordGenerator().generatePassword()
                        currentPassword = newPassword

                    }
                },
                colors = actionColor()
            ) {
                Text(text = stringResource(id = R.string.generate_new_password))
            }
        }
    }

    @Composable
    fun setNewCode() {
        val contentResolver = context.getContentResolver()
        val isProvisioned = Settings.Global.getInt(
            contentResolver,
            Settings.Global.DEVICE_PROVISIONED
        ) == 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    scope.launch {
                        persistentStorage.savePassword(currentPassword)
                        storedPassword = currentPassword
                        persistentStorage.saveStatus(true)
                        Toast.makeText(
                            context,
                            R.string.code_set_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        if (isProvisioned) {
                            onCodeSet()
                        }
                        showUpdatePrompt =
                            persistentStorage.passwordsMirrorNeedsUpdate(currentPassword)
                    }
                },
                enabled = currentPassword.isNotEmpty() && currentPassword != storedPassword,
                colors = buttonColor()
            ) {
                Text(text = stringResource(id = R.string.set))
            }
        }
    }

    @Composable
    fun displayTestProcedure() {
        val grayColor = Color.Gray
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = grayColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.find_my_phone_test),
                contentDescription = "",
                tint = colorResource(foundation.e.elib.R.color.e_icon_color),
                modifier = Modifier.size(30.dp)

            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.password_test_procedure),
                color = grayColor,
                fontSize = 14.sp
            )
        }
    }

    @Composable
    fun displayNextButton() {

        val contentResolver = context.getContentResolver()
        val isProvisioned = Settings.Global.getInt(
            contentResolver,
            Settings.Global.DEVICE_PROVISIONED
        ) == 1;

        if (!isProvisioned) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            onSelection()
                            persistentStorage.savePassword(currentPassword)
                            persistentStorage.saveStatus(true)
                        }
                    },
                    colors = buttonColor()
                ) {
                    Text(text = stringResource(id = R.string.password_next))
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {

        Text(
            text = stringResource(id = R.string.welcome_screen_intro_1),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        Text(
            text = stringResource(id = R.string.welcome_screen_intro_2),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        Text(
            text = stringResource(id = R.string.current_password),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayPassword()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        generateNewCode()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayTestProcedure()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        setNewCode()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        saveIntoPasswordsButton(
            code = currentPassword,
            storedCode = storedPassword,
            onSaved = { persistentStorage.savePasswordsMirrorCode(currentPassword) }
        )

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayNextButton()
    }

    updateInPasswordsDialog(
        visible = showUpdatePrompt,
        code = currentPassword,
        onFinished = { saved ->
            if (saved) persistentStorage.savePasswordsMirrorCode(currentPassword)
            showUpdatePrompt = false
        }
    )
}


private val PROGRESS_SIZE = 20.dp
private val PROGRESS_STROKE = 2.dp

@SuppressLint("ComposableNaming")
@Composable
private fun saveIntoPasswordsButton(
    code: String,
    storedCode: String,
    onSaved: () -> Unit
) {
    if (code.isEmpty() || code != storedCode) return
    val context = LocalContext.current
    val save = rememberPasswordsSaver { result ->
        if (result is Saved) onSaved()
        Toast.makeText(context, result.toastMessage(), Toast.LENGTH_LONG).show()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = { save.save(code) }, enabled = !save.busy, colors = actionColor()) {
            if (save.busy) {
                CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE), strokeWidth = PROGRESS_STROKE)
            } else {
                Text(text = stringResource(id = R.string.save_into_passwords))
            }
        }
    }
}

private data class CodeState(
    val current: String,
    val stored: String,
    val statusOn: Boolean
)

/**
 * Loads the code to show and whether it counts as already set.
 *
 * A code is only "stored" once the user has activated it with Set — the status flag. A code that
 * was generated on first entry is persisted immediately but is not in use until then, so deriving
 * "set" from persistence would leave an unconfirmed code looking active, block Set, and offer to
 * mirror a code that no SMS would match.
 */
private fun PersistentStorage.loadCodeState(): CodeState {
    val saved = getPassword()?.takeIf { it.isNotEmpty() }
        ?: PasswordGenerator().generatePassword().also { savePassword(it) }
    val statusOn = getStatus()
    return CodeState(
        current = saved,
        stored = if (statusOn) saved else "",
        statusOn = statusOn
    )
}

@SuppressLint("ComposableNaming")
@Composable
private fun updateInPasswordsDialog(visible: Boolean, code: String, onFinished: (Boolean) -> Unit) {
    if (!visible) return
    val context = LocalContext.current

    val save = rememberPasswordsSaver { result ->
        Toast.makeText(context, result.toastMessage(), Toast.LENGTH_LONG).show()
        onFinished(result is Saved)
    }

    AlertDialog(
        onDismissRequest = { if (!save.busy) onFinished(false) },
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text(text = stringResource(id = R.string.update_in_passwords_title)) },
        text = { Text(text = stringResource(id = R.string.update_in_passwords_message)) },
        confirmButton = {
            TextButton(
                colors = actionColor(),
                enabled = !save.busy,
                onClick = { save.save(code) }
            ) {
                if (save.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE), strokeWidth = PROGRESS_STROKE)
                } else {
                    Text(text = stringResource(id = R.string.update_in_passwords_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(colors = actionColor(), enabled = !save.busy, onClick = { onFinished(false) }) {
                Text(text = stringResource(id = R.string.update_in_passwords_dismiss))
            }
        }
    )
}
