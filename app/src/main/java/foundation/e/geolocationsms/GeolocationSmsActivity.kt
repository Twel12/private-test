package foundation.e.geolocationsms

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource


class GeolocationSmsActivity : ComponentActivity() {

    companion object {
        const val TAG = "GeolocationSmsActivity"
    }
    private lateinit var persistentStorage: PersistentStorage
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        val permissions = arrayOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS
        )

        permissionManager.checkAndRequestPermissions(permissions) { granted ->
            if (!granted) {
                Log.e(TAG, "Permission error")
                Toast.makeText(this, getString(R.string.generated_password), Toast.LENGTH_SHORT).show()
                return@checkAndRequestPermissions
            }
        }

        persistentStorage = PersistentStorage(this)
        setContent {
            //GeoSmsTheme { //K1ZFP TODO Add theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    passwordScreen(persistentStorage)
                }
            //}
        }
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun passwordScreen(persistentStorage: PersistentStorage) {
    //val context = LocalContext.current
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
        Text(text = stringResource(id = R.string.generated_password), style = MaterialTheme.typography.bodyLarge)
        Text(text = generatedPassword, style = MaterialTheme.typography.headlineMedium)
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
        if (isPasswordCorrect) {
            Text(text = stringResource(id = R.string.password_correct), style = MaterialTheme.typography.bodyLarge)
        } else if (enteredPassword.isNotEmpty() && enteredPassword.length == PasswordGenerator.PASSWORD_LENGTH) {
            Text(text = stringResource(id = R.string.password_incorrect), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
