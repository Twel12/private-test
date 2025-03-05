package foundation.e.geolocationsms.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foundation.e.geolocationsms.storage.PersistentStorage
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.activity.GeolocationSmsActivity
import foundation.e.geolocationsms.receiver.UiReceiver
import foundation.e.geolocationsms.ui.buttons.ToggleWithText
import foundation.e.geolocationsms.ui.text.CustomTopAppBar
import foundation.e.geolocationsms.util.Dimens
import kotlinx.coroutines.launch

/**
 * WelcomeScreen
 *
 * This class implements the initial screen displayed to the user upon launching the application.
 **/
object WelcomeScreen : ScreenInterface {
    @Composable
    override fun displayScreen() {
        WelcomeScreenContent()
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreenContent()
}

@Composable
fun WelcomeScreenContent() {

    val context = LocalContext.current
    val mActivity = LocalActivity.current
    val persistentStorage = PersistentStorage(context)
    val scope = rememberCoroutineScope()
    var isSwitchChecked by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        scope.launch {
            val savedStatus = persistentStorage.getStatus()
            isSwitchChecked = savedStatus
        }
    }

    Column(
        modifier =
        Modifier.fillMaxSize()
            .padding(start = Dimens.SCREEN_PADDING, end = Dimens.SCREEN_PADDING),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {

        val clipboardManager = LocalClipboardManager.current

        Text(
            text = stringResource(R.string.welcome_screen_caption),
            color = colorResource(foundation.e.elib.R.color.e_primary_text_color),
            fontSize = 15.sp,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(bottom = Dimens.SCREEN_PADDING / 2)
        )

        val docUrl = stringResource(R.string.e_foundation_docs_link) // K1ZFP TODO update
        Text(
            text = docUrl,
            fontSize = 15.sp,
            color = colorResource(foundation.e.elib.R.color.e_accent),
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
            style = TextStyle(textDecoration = TextDecoration.Underline),
            modifier =
            Modifier.padding(bottom = 8.dp).pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        try {
                            val customTabsIntent =
                                CustomTabsIntent.Builder().setShowTitle(true).build()
                            if (mActivity != null) {
                                customTabsIntent.launchUrl(mActivity, Uri.parse(docUrl))
                            }
                        } catch (e: Exception) {
                            // Fallback to default browser
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(docUrl))
                            context.startActivity(intent)
                        }
                    },
                    onLongPress = {
                        // Copy to clipboard
                        clipboardManager.setText(AnnotatedString(docUrl))
                        Toast.makeText(
                            mActivity,
                            context.getString(R.string.link_copied_to_clipboard),
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                )
            }
        )

        ToggleWithText(
            text = stringResource(R.string.welcome_screen_on_off),
            isChecked = isSwitchChecked,
            fontWeight = FontWeight.Medium,
            onCheckedChange = { isChecked ->
                isSwitchChecked = isChecked
                persistentStorage.saveStatus(isChecked)
                Log.d("WelcomeScreen", "Switch is now ${if (isChecked) "ON" else "OFF"}")
            }
        )

    }
}

