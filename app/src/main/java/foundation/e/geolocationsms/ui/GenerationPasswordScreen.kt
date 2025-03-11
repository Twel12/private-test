package foundation.e.geolocationsms.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foundation.e.geolocationsms.util.PasswordGenerator
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.ui.buttons.actionColor
import foundation.e.geolocationsms.ui.buttons.buttonColor
import foundation.e.geolocationsms.ui.buttons.toggleWithText
import foundation.e.geolocationsms.util.Dimens
import kotlinx.coroutines.launch

/**
 * GenerationPasswordScreen
 *
 * This class implements a screen within the application's user interface that
 * is responsible for generating and displaying a new, random password.
 **/
object GenerationPasswordScreen : ScreenInterface{

    internal const val EMPTY_CODE = "--------"
    internal const val CODE_COLOR = 0xFF1A9E24
    internal const val TAG = "GenerationPasswordScreen"

    @Composable
    override fun displayScreen(onBackPressed: () -> Unit, onSelection: () -> Unit) {
        BackHandler(onBack = { onBackPressed() })
        generatePasswordScreenContent(onSelection)
    }
}


@Preview(showBackground = true)
@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenPreview() {
    generatePasswordScreenContent {}
}

@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenContent(onSelection: () -> Unit) {
    val context = LocalContext.current
    val persistentStorage = PersistentStorage(context)
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var isSwitchChecked by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        scope.launch {
            val savedPassword = persistentStorage.getPassword()
            if (savedPassword != null) {
                currentPassword = savedPassword
            }
            val savedStatus = persistentStorage.getStatus()
            isSwitchChecked = savedStatus
        }
    }

    @Composable
    fun displayCharacter(char: Char) {
        Text(
            text = char.toString(),
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.15.sp,
            textAlign = TextAlign.Center,
            color = Color(GenerationPasswordScreen.CODE_COLOR),
        )
    }

    @Composable
    fun displayPassword(password: String) {
        var displayedPassword : String = password
        if (password.isEmpty()) {
            displayedPassword = GenerationPasswordScreen.EMPTY_CODE
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 25.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            displayedPassword.forEach { char ->
                displayCharacter(char = char)
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
                        persistentStorage.savePassword(currentPassword)
                    }
                },
                colors = actionColor()
            ) {
                Text(text = stringResource(id = R.string.generate_new_password))
            }
        }
    }

    @Composable
    fun displayNextButton() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    scope.launch {
                        onSelection()
                    }
                },
                colors = buttonColor()
            ) {
                Text(text = stringResource(id = R.string.password_next))
            }
        }
    }

    @Composable
    fun displayStatus() {
        toggleWithText(
            text = stringResource(R.string.welcome_screen_on_off),
            isChecked = isSwitchChecked,
            fontWeight = FontWeight.Medium,
            onCheckedChange = { isChecked ->
                isSwitchChecked = isChecked
                persistentStorage.saveStatus(isChecked)
                Log.d(GenerationPasswordScreen.TAG,
                    "Switch is now ${if (isChecked) "ON" else "OFF"}")
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        displayStatus()

        Text(text = stringResource(id = R.string.welcome_screen_intro_1),
            style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        Text(text = stringResource(id = R.string.welcome_screen_intro_2),
            style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        Text(text = stringResource(id = R.string.current_password),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayPassword(currentPassword)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        generateNewCode()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayNextButton()
    }
}

