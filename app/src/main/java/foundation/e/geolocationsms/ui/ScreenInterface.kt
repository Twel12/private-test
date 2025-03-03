package foundation.e.geolocationsms.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable

/**
 * ScreenInterface
 *
 * This interface defines a contract for all screens within the application's user interface.
 **/
interface ScreenInterface {
    @SuppressLint("ComposableNaming")
    @Composable
    fun displayScreen()
}
