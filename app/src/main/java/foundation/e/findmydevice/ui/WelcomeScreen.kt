package foundation.e.findmydevice.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foundation.e.findmydevice.storage.PersistentStorage
import foundation.e.findmydevice.R
import foundation.e.findmydevice.activity.FindMyDeviceActivity
import foundation.e.findmydevice.ui.buttons.actionColor
import foundation.e.findmydevice.ui.buttons.toggleWithText
import foundation.e.findmydevice.util.Dimens
import foundation.e.findmydevice.util.hasSimSupport
import kotlinx.coroutines.launch

/**
 * WelcomeScreen
 *
 * This class implements the initial screen displayed to the user upon launching the application.
 **/
object WelcomeScreen {
    internal const val TAG = "WelcomeScreen"

    @SuppressLint("ComposableNaming")
    @Composable
    fun displayScreen(onBackPressed: () -> Unit, onSelection: () -> Unit) {
        BackHandler(onBack = { onBackPressed() })
        welcomeScreenContent(onSelection, onBackPressed)
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun welcomeScreenContent(onSelection: () -> Unit,
                         onBackPressed: () -> Unit) {

    val context = LocalContext.current
    val persistentStorage = PersistentStorage(context)
    val scope = rememberCoroutineScope()
    var isSwitchChecked by remember { mutableStateOf(false) }

    val hasSimSupport = hasSimSupport(context)

    LaunchedEffect(key1 = true) {
        scope.launch {
            val savedStatus = persistentStorage.getStatus()
            isSwitchChecked = savedStatus
        }
    }

    @Composable
    fun drawIcon() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.SCREEN_PADDING / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                painter = painterResource(id = R.drawable.find_my_phone),
                contentDescription = "",
                tint = colorResource(foundation.e.elib.R.color.e_accent),
                modifier = Modifier.size(50.dp)
            )
        }
    }

    @Composable
    fun drawSwitch(persistentStorage: PersistentStorage) {
        toggleWithText(
            text = stringResource(R.string.welcome_screen_on_off),
            isChecked = isSwitchChecked,
            fontWeight = FontWeight.Medium,
            onCheckedChange = { isChecked ->
                persistentStorage.saveStatus(isChecked)
                isSwitchChecked = isChecked
                Log.d(WelcomeScreen.TAG, "Switch is now ${if (isChecked) "ON" else "OFF"}")
                onSelection()
            }
        )
    }

    @SuppressLint("ComposableNaming")
    @Composable
    fun drawCaption() {
        Text(
            text = stringResource(R.string.welcome_screen_title),
            color = colorResource(foundation.e.elib.R.color.e_primary_text_color),
            fontSize = 25.sp,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(bottom = Dimens.SCREEN_PADDING / 2)
        )
    }

    @Composable
    fun manageCode() {
        if (FindMyDeviceActivity.persistentStorage.getStatus()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            onSelection()
                        }
                    },
                    colors = actionColor()
                ) {
                    Text(text = stringResource(id = R.string.manage_secret_password))
                }
            }
        }
    }

    @Composable
    fun drawText(text: String) {
        Text(
            text = text,
            color = colorResource(foundation.e.elib.R.color.e_primary_text_color),
            fontSize = 15.sp,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
        )
    }

    @Composable
    fun hasSim(hasSimSupport: Boolean, onBackPressed: () -> Unit) {
        if (!hasSimSupport) {
            AlertDialog(
                onDismissRequest = { onBackPressed() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.no_sim_caption),
                            fontSize = 20.sp,
                            modifier = Modifier.padding(start = Dimens.SCREEN_PADDING / 2)
                        )
                    }
                },
                text = {
                    Text(
                        text = stringResource(R.string.no_sim_details),
                        color =
                            colorResource(id = foundation.e.elib.R.color.e_secondary_text_color)
                    )
                },
                confirmButton = {
                    Text(
                        modifier =
                            Modifier
                                .clickable { onBackPressed() }
                                .padding(start = Dimens.SCREEN_PADDING / 2),
                        color = colorResource(id = foundation.e.elib.R.color.e_accent),
                        text = stringResource(R.string.no_sim_button),
                        fontSize = 14.sp
                    )
                }
            )
        }
    }
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.SCREEN_PADDING, end = Dimens.SCREEN_PADDING),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        drawIcon()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        drawCaption()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        drawText(stringResource(R.string.welcome_screen_caption_1))

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        drawText(stringResource(R.string.welcome_screen_caption_2))

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        drawSwitch(persistentStorage)

        manageCode()
    }

    hasSim(hasSimSupport, onBackPressed)
}
