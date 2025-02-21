package foundation.e.geolocationsms


import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import androidx.activity.compose.setContent


class GeolocationSmsActivity : ComponentActivity() {

    companion object {
        const val PASSORD_KEY = "password"
        const val PREFERENCE_STORE = "GeoSmsPrefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            //GeoSmsTheme { //K1ZFP TODO Add theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PasswordScreen()
                }
            //}
        }
    }
}

@Composable
fun PasswordScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var generatedPassword by remember { mutableStateOf("") }
    var enteredPassword by remember { mutableStateOf("") }
    var isPasswordCorrect by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        scope.launch {
            val sharedPreferences = context.getSharedPreferences(GeolocationSmsActivity.PREFERENCE_STORE, Context.MODE_PRIVATE)
            val savedPassword = sharedPreferences.getString(GeolocationSmsActivity.PASSORD_KEY, null)
            if (savedPassword != null) {
                generatedPassword = savedPassword
            } else {
                val newPassword = "ABCDEF12"
                generatedPassword = newPassword
                with(sharedPreferences.edit()) {
                    putString(GeolocationSmsActivity.PASSORD_KEY, newPassword)
                    apply()
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Generated Password:", style = MaterialTheme.typography.bodyLarge)
        Text(text = generatedPassword, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                val newPassword = generatedPassword.toCharArray().toMutableList().apply { shuffle() }.joinToString("")
                generatedPassword = newPassword
                enteredPassword = ""
                isPasswordCorrect = false
                val sharedPreferences = context.getSharedPreferences(GeolocationSmsActivity.PREFERENCE_STORE, Context.MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putString(GeolocationSmsActivity.PASSORD_KEY, newPassword)
                    apply()
                }
            }
        }) {
            Text("Generate New Password")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = enteredPassword,
            onValueChange = {
                enteredPassword = it
                isPasswordCorrect = it == generatedPassword
            },
            label = { Text("Enter Password") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isPasswordCorrect) {
            Text("Password Correct!", style = MaterialTheme.typography.bodyLarge)
        } else if (enteredPassword.isNotEmpty()) {
            Text("Incorrect Password", style = MaterialTheme.typography.bodyLarge)
        }
    }
}