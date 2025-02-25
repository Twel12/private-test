package foundation.e.geolocationsms

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import foundation.e.geolocationsms.ui.PasswordScreen
import kotlinx.coroutines.launch


class GeolocationSmsActivity : ComponentActivity() {

    companion object {
        const val TAG = "GeolocationSmsActivity"
    }
    private lateinit var persistentStorage: PersistentStorage
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        val permissions = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionManager.checkAndRequestPermissions(permissions) { granted ->
            if (!granted) {
                Log.e(TAG, "Permission error")
                Toast.makeText(this, getString(R.string.generated_password), Toast.LENGTH_SHORT).show()
                return@checkAndRequestPermissions
            }
        }

        //For testing
        //val intent = Intent("foundation.e.accountmanager.ui.setup.CreateAccountActivity")
        //startActivity(intent)

        persistentStorage = PersistentStorage(this)
        setContent {
            //GeoSmsTheme { //K1ZFP TODO Add theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PasswordScreen.passwordScreen(persistentStorage)
                }
            //}
        }
    }
}
