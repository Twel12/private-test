package foundation.e.geolocationsms

import android.content.Intent

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import foundation.e.geolocationsms.receiver.UiReceiver.Companion.UI_ACTION_KEY
import foundation.e.geolocationsms.receiver.UiReceiver.Companion.UI_ACTION_NEW_PASSWORD
import foundation.e.geolocationsms.receiver.UiReceiver.Companion.UI_ACTION_STATUS
import foundation.e.geolocationsms.ui.GenerationPasswordScreen
import foundation.e.geolocationsms.ui.WelcomeScreen


class GeolocationSmsActivity : FragmentActivity() {

    companion object {
        const val TAG = "GeolocationSmsActivity"
        lateinit var persistentStorage: PersistentStorage
    }

    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent.action
        Log.d(TAG, "Action: $action")

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

        if (intent.hasExtra(UI_ACTION_KEY)) {
            if (UI_ACTION_NEW_PASSWORD == intent.getStringExtra(UI_ACTION_KEY)) {

                val biometricManager = BiometricManager.from(this)
                when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        showBiometricPrompt()
                    }
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                        displayGeneratePasswordScreen()
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                        Log.d(TAG, "Biometric hardware is currently unavailable")
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                        displayGeneratePasswordScreen()
                }
            }
        } else if (intent.hasExtra(UI_ACTION_KEY)) {
            if (UI_ACTION_NEW_PASSWORD == intent.getStringExtra(UI_ACTION_STATUS)) {
                //
            }
        } else {
            displayWelomePasswordScreen()
        }

    }
    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                //handleFailedUnlock()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                displayGeneratePasswordScreen()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                //handleFailedUnlock()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }


    private fun displayWelomePasswordScreen() {
        setContent {
            //GeoSmsTheme { //K1ZFP TODO Add theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                WelcomeScreen.passwordScreen()
            }
        }
    }
    private fun displayGeneratePasswordScreen() {
        setContent {
            //GeoSmsTheme { //K1ZFP TODO Add theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                GenerationPasswordScreen.passwordScreen(persistentStorage)
            }
        }
    }


}
