package foundation.e.findmydevice.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foundation.e.findmydevice.storage.PersistentStorage
import foundation.e.findmydevice.R
import foundation.e.findmydevice.activity.FindMyDeviceActivity
import foundation.e.findmydevice.ui.buttons.actionColor
import foundation.e.findmydevice.ui.buttons.toggleWithText
import foundation.e.findmydevice.util.Dimens
import kotlinx.coroutines.launch

/**
 * NoSIMScreen
 *
 **/
object NoSimScreen {
    internal const val TAG = "WelcomeScreen"

    @SuppressLint("ComposableNaming")
    @Composable
    fun displayScreen(onBackPressed: () -> Unit) {
        BackHandler(onBack = { onBackPressed() })
        Text(
            text = "No SIM", //TODO
            color = colorResource(foundation.e.elib.R.color.e_primary_text_color),
            fontSize = 15.sp,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
        )
    }
}


