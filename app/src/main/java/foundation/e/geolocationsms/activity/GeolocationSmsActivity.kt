package foundation.e.geolocationsms.activity

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import foundation.e.geolocationsms.util.PermissionManager
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.receiver.UiReceiver.Companion.UI_ACTION_CHECK_PASSWORD
import foundation.e.geolocationsms.receiver.UiReceiver.Companion.UI_ACTION_KEY
import foundation.e.geolocationsms.receiver.UiReceiver.Companion.UI_ACTION_NEW_PASSWORD
import foundation.e.geolocationsms.ui.ConfirmationPasswordScreen
import foundation.e.geolocationsms.ui.GenerationPasswordScreen
import foundation.e.geolocationsms.ui.WelcomeScreen

/**
 * GeolocationSmsActivity
 *
 * This Activity serves as the main entry point for the GeolocationSMS application.
 **/
class GeolocationSmsActivity : FragmentActivity() {

    companion object {
        const val TAG = "GeolocationSmsActivity"
        lateinit var persistentStorage: PersistentStorage
    }

    private lateinit var permissionManager: PermissionManager

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent.action
        Log.d(TAG, "Action: $action")

        permissionManager = PermissionManager(this)

        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION
        )
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)

        permissionManager.checkAndRequestPermissions(permissions) { granted ->
            if (!granted) {
                Log.e(TAG, "Permission error")
                Toast.makeText(this, getString(R.string.check_permission), Toast.LENGTH_SHORT).show()
                return@checkAndRequestPermissions
            } else {
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                permissionManager.checkAndRequestPermissions(permissions) { granted ->
                    if (!granted) {
                        Log.e(TAG, "Permission error (2)")
                        Toast.makeText(this, getString(R.string.check_permission), Toast.LENGTH_SHORT).show()
                        return@checkAndRequestPermissions
                    }
                }
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
            } else if (UI_ACTION_CHECK_PASSWORD == intent.getStringExtra(UI_ACTION_KEY)) {
                displayCheckPasswordScreen()
            }
        } else {
            displayWelcomePasswordScreen()
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
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }



    //region Display screen

    private fun displayWelcomePasswordScreen() {
        setContent {
            //GeoSmsTheme { //K1ZFP TODO Add theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                WelcomeScreen.displayScreen()
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
                GenerationPasswordScreen.displayScreen()
            }
        }
    }
    private fun displayCheckPasswordScreen() {
        setContent {
            //GeoSmsTheme { //K1ZFP TODO Add theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ConfirmationPasswordScreen.displayScreen()
            }
        }
    }
    // endregion

}
