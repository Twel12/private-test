package foundation.e.geolocationsms.ui

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.receiver.UiReceiver
import kotlinx.coroutines.launch

class WelcomeScreen {

    companion object {

        @SuppressLint("ComposableNaming")
        @Composable
        fun passwordScreen() {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(id = R.string.welcome_screen_title),
                    style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(id = R.string.welcome_screen_caption),
                    style = MaterialTheme.typography.bodyLarge)

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
                            val intent = Intent(UiReceiver.UI_ACTION_STATUS)
                            intent.setPackage(context.packageName)
                            context.sendBroadcast(intent)
                        }
                    }) {
                        Text(text = stringResource(id = R.string.welcome_screen_view_password))
                    }
                }

            }
        }
    }
}