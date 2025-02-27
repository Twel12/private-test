package foundation.e.geolocationsms.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import foundation.e.geolocationsms.PersistentStorage
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.receiver.UiReceiver
import kotlinx.coroutines.launch

object WelcomeScreen : ScreenInterface{

    @Composable
    override fun displayScreen() {
        val context = LocalContext.current
        val persistentStorage = PersistentStorage(context)
        val scope = rememberCoroutineScope()
        var isSwitchChecked by remember { mutableStateOf(false) }

        LaunchedEffect(key1 = true) {
            scope.launch {
                val savedStatus = persistentStorage.getStatus()
                isSwitchChecked = savedStatus
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(id = R.string.welcome_screen_title),
                style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.welcome_screen_caption),
                style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(id = R.string.welcome_screen_on_off))
                Switch(
                    checked = isSwitchChecked,
                    onCheckedChange = { isChecked ->
                        isSwitchChecked = isChecked
                        persistentStorage.saveStatus(isChecked)
                        Log.d("WelcomeScreen", "Switch is now ${if (isChecked) "ON" else "OFF"}")
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(onClick = {
                    scope.launch {
                        val intent = Intent(UiReceiver.UI_ACTION_NEW_PASSWORD)
                        intent.setPackage(context.packageName)
                        context.sendBroadcast(intent)
                    }
                }) {
                    Text(text = stringResource(id = R.string.welcome_screen_new_password))
                }
                Button(onClick = {
                    scope.launch {
                        val intent = Intent(UiReceiver.UI_ACTION_CHECK_PASSWORD)
                        intent.setPackage(context.packageName)
                        context.sendBroadcast(intent)
                    }
                }) {
                    Text(text = stringResource(id = R.string.welcome_screen_check_password))
                }
            }

        }
    }
}
