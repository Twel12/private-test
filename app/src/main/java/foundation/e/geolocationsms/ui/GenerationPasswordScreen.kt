package foundation.e.geolocationsms.ui

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
import androidx.compose.ui.unit.dp
import foundation.e.geolocationsms.PasswordGenerator
import foundation.e.geolocationsms.PersistentStorage
import foundation.e.geolocationsms.R
import kotlinx.coroutines.launch

object GenerationPasswordScreen : ScreenInterface{

    @Composable
    override fun displayScreen() {
        val context = LocalContext.current
        val persistentStorage = PersistentStorage(context)
        val scope = rememberCoroutineScope()
        var generatedPassword by remember { mutableStateOf("") }
        var currentPassword by remember { mutableStateOf("") }

        LaunchedEffect(key1 = true) {
            scope.launch {
                val savedPassword = persistentStorage.getPassword()
                generatedPassword = ""
                if (savedPassword != null) {
                    currentPassword = savedPassword
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
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
}

