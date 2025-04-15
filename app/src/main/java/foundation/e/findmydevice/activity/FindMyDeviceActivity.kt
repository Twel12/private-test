package foundation.e.findmydevice.activity

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import foundation.e.findmydevice.R
import foundation.e.findmydevice.data.Pages
import foundation.e.findmydevice.storage.PersistentStorage
import foundation.e.findmydevice.ui.ConfirmationPasswordScreen
import foundation.e.findmydevice.ui.GenerationPasswordScreen
import foundation.e.findmydevice.ui.WelcomeScreen
import foundation.e.findmydevice.ui.text.customTopAppBar
import foundation.e.findmydevice.ui.theme.findMyDeviceTheme
import foundation.e.findmydevice.util.PermissionManager


/**
 * FindMyDeviceActivity
 *
 * This Activity serves as the main entry point for the FindMyDevice application.
 **/
class FindMyDeviceActivity : FragmentActivity() {

    companion object {
        const val TAG = "FindMyDeviceActivity"
        lateinit var persistentStorage: PersistentStorage
    }

    private lateinit var permissionManager: PermissionManager

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        persistentStorage = PersistentStorage(this)

        permissionManager = PermissionManager(this)
        permissionManager.checkAndRequestPermissionsWithBackground {  }
        displayPage(Pages.ActivateFeature)
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
            findMyDeviceTheme {
                window.statusBarColor = MaterialTheme.colorScheme.background.toArgb()
                window.navigationBarColor = MaterialTheme.colorScheme.background.toArgb()
                Surface(color = MaterialTheme.colorScheme.background) {

                    val appBarTitle = remember { mutableStateOf(getTitleForPage(page)) }
                    val configuration = LocalConfiguration.current
                    val isLandscape =
                        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    Column(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .let {
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
                                    findMyDeviceActivity = this@FindMyDeviceActivity
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
