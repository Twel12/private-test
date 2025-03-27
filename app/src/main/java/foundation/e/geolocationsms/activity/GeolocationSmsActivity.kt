package foundation.e.geolocationsms.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.fragment.app.FragmentActivity
import foundation.e.geolocationsms.util.PermissionManager
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.data.Pages
import foundation.e.geolocationsms.ui.theme.geolocationSmsTheme
import foundation.e.geolocationsms.ui.ConfirmationPasswordScreen
import foundation.e.geolocationsms.ui.GenerationPasswordScreen
import foundation.e.geolocationsms.ui.WelcomeScreen
import foundation.e.geolocationsms.ui.text.customTopAppBar

import android.telephony.TelephonyManager
/**
 * GeolocationSmsActivity
 *
 * This Activity serves as the main entry point for the GeolocationSMS application.
 **/
class GeolocationSmsActivity : FragmentActivity() {

    companion object {
        const val TAG = "GeolocationSmsActivity"
        lateinit var persistentStorage: PersistentStorage
        const val APP_FIND_MY_DEVICE_ASK_STATUS = "FMD_ASK_STATUS" // Shared with parental control
    }

    private lateinit var permissionManager: PermissionManager

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        persistentStorage = PersistentStorage(this)

        val intent = intent
        // Ask for status. Do not really start the activity
        val fmdAskStatus : Boolean= intent.getBooleanExtra(APP_FIND_MY_DEVICE_ASK_STATUS, false)
        if (fmdAskStatus) {
            val resultIntent = Intent()
            resultIntent.putExtra(APP_FIND_MY_DEVICE_ASK_STATUS, persistentStorage.getStatus())
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }

        permissionManager = PermissionManager(this)
        permissionManager.checkAndRequestPermissionsWithBackground() {granted ->
            if (!granted){
                return@checkAndRequestPermissionsWithBackground
            }
        }

        if (hasSimSupport(this)) {
            displayPage(Pages.ActivateFeature)
        }
    }

    private fun hasSimSupport(context: Context): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> {
                Log.d(TAG, "SIM OK")
                true
            }
            TelephonyManager.SIM_STATE_ABSENT -> {
                Log.d(TAG, "SIM Not found")
                false
            }
            else -> {
                Log.d(TAG, "Invalid SIM State: ${telephonyManager.simState}")
                false
            }
        }
    }

    private fun onExitApp(withResult: Boolean = false) {
        if (withResult) {
            setResult(RESULT_OK)
        }
        finishAfterTransition()
    }

    private fun getTitleForPage(page: Pages) = when(page){
        Pages.ActivateFeature -> getString(R.string.title_welcome)
        Pages.GeneratePassword -> getString(R.string.title_generate_password)
        Pages.CheckPassword -> getString(R.string.title_check_password)
    }

    //region Display screen
    fun displayPage(page: Pages){
        setContent {
            geolocationSmsTheme {
                window.statusBarColor = MaterialTheme.colorScheme.background.toArgb()
                window.navigationBarColor = MaterialTheme.colorScheme.background.toArgb()
                Surface(color = MaterialTheme.colorScheme.background) {

                    val appBarTitle = remember { mutableStateOf(getTitleForPage(page)) }
                    val configuration = LocalConfiguration.current
                    val isLandscape =
                        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    Column(
                        modifier =
                        Modifier.fillMaxSize().let {
                            if (isLandscape) it.verticalScroll(rememberScrollState()) else it
                        },
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top
                    ) {
                        BackHandler(onBack = {
                            onExitApp() }
                        )
                        customTopAppBar(
                            title = appBarTitle.value,
                            onClick = { onExitApp() }
                        )
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Top
                        ) {
                            when (page) {
                                Pages.ActivateFeature -> WelcomeScreen.displayScreen(
                                    onBackPressed = {
                                        Log.d(TAG, "A BACK")
                                        onExitApp() },
                                    onSelection = {
                                        Log.d(TAG, "A SEL")
                                        if (persistentStorage.getStatus())
                                            displayPage(Pages.GeneratePassword)
                                        else
                                            displayPage(Pages.ActivateFeature) }
                                )
                                Pages.GeneratePassword ->GenerationPasswordScreen.displayScreen(
                                    onBackPressed = { Log.d(TAG, "G BACK")},
                                    onSelection = {
                                        Log.d(TAG, "G SEL")
                                        onExitApp(true) },
                                    geolocationSmsActivity = this@GeolocationSmsActivity
                                )
                                Pages.CheckPassword -> ConfirmationPasswordScreen.displayScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    // endregion

}
