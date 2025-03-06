package foundation.e.geolocationsms.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import foundation.e.geolocationsms.util.PasswordGenerator
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.ui.buttons.toggleWithText
import kotlinx.coroutines.launch

/**
 * GenerationPasswordScreen
 *
 * This class implements a screen within the application's user interface that
 * is responsible for generating and displaying a new, random password.
 **/
object GenerationPasswordScreen : ScreenInterface{
    @Composable
    override fun displayScreen() {
        generatePasswordScreenContent()
    }
}

@Preview(showBackground = true)
@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenPreview() {
    generatePasswordScreenContent()
}

@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenContent() {
    val context = LocalContext.current
    val persistentStorage = PersistentStorage(context)
    val scope = rememberCoroutineScope()
    var generatedPassword by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var isSwitchChecked by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        scope.launch {
            val savedPassword = persistentStorage.getPassword()
            generatedPassword = ""
            if (savedPassword != null) {
                currentPassword = savedPassword
            }
            val savedStatus = persistentStorage.getStatus()
            isSwitchChecked = savedStatus
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        toggleWithText(
            text = stringResource(R.string.welcome_screen_on_off),
            isChecked = isSwitchChecked,
            fontWeight = FontWeight.Medium,
            onCheckedChange = { isChecked ->
                isSwitchChecked = isChecked
                persistentStorage.saveStatus(isChecked)
                Log.d("WelcomeScreen", "Switch is now ${if (isChecked) "ON" else "OFF"}")
            }
        )

        Text(text = stringResource(id = R.string.current_password), style = MaterialTheme.typography.bodyLarge)
        Text(text = currentPassword, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = stringResource(id = R.string.generated_password), style = MaterialTheme.typography.bodyLarge)
        Text(text = generatedPassword, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                scope.launch {
                    val newPassword = PasswordGenerator().generatePassword()
                    generatedPassword = newPassword
                }
            }) {
                Text(text = stringResource(id = R.string.generate_new_password))
            }

            Button(onClick = {
                scope.launch {
                    persistentStorage.savePassword(generatedPassword)
                    currentPassword = generatedPassword
                }
            }) {
                Text(text = stringResource(id = R.string.password_save))
            }
        }
    }
}

