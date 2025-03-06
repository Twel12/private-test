package foundation.e.geolocationsms.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import foundation.e.geolocationsms.util.PasswordGenerator
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.R
import kotlinx.coroutines.launch

/**
 * ConfirmationPasswordScreen
 *
 * This class implements a screen within the application's user interface that
 * allows the user to confirm a previously generated password.
 **/
object ConfirmationPasswordScreen: ScreenInterface {
    @Composable
    override fun displayScreen() {
        generateConfirmationPasswordScreen()
    }
}

@Preview(showBackground = true)
@SuppressLint("ComposableNaming")
@Composable
fun generateConfirmationPasswordScreenPreview() {
    generateConfirmationPasswordScreen()
}

@Composable
fun generateConfirmationPasswordScreen() {
    val context = LocalContext.current
    val persistentStorage = PersistentStorage(context)
    val scope = rememberCoroutineScope()
    var generatedPassword by remember { mutableStateOf("") }
    var enteredPassword by remember { mutableStateOf("") }
    var isPasswordCorrect by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        scope.launch {
            val savedPassword = persistentStorage.getPassword()
            if (savedPassword != null) {
                generatedPassword = savedPassword
            } else {
                val newPassword = PasswordGenerator().generatePassword()
                generatedPassword = newPassword
                persistentStorage.savePassword(generatedPassword)
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = enteredPassword,
            onValueChange = {
                enteredPassword = it
                if (it.length == PasswordGenerator.PASSWORD_LENGTH) {
                    isPasswordCorrect = it == generatedPassword
                    persistentStorage.addCheckedPasswordResult(isPasswordCorrect)
                } else {
                    isPasswordCorrect = false
                }
            },
            label = { Text(text = stringResource(id = R.string.enter_password)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            scope.launch {
                val newPassword = PasswordGenerator().generatePassword()
                generatedPassword = newPassword
                enteredPassword = ""
                isPasswordCorrect = false
                persistentStorage.savePassword(newPassword)
            }
        }) {
            Text(text = stringResource(id = R.string.generate_new_password))
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isPasswordCorrect) {
            Text(text = stringResource(id = R.string.password_correct),
                style = MaterialTheme.typography.bodyLarge)
        } else if (enteredPassword.isNotEmpty() && enteredPassword.length ==
            PasswordGenerator.PASSWORD_LENGTH) {
            Text(text = stringResource(id = R.string.password_incorrect),
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}
