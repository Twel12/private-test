package foundation.e.geolocationsms.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.ClipboardManager
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
import foundation.e.geolocationsms.ui.buttons.toggleWithText
import foundation.e.geolocationsms.util.Dimens
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * WelcomeScreen
 *
 * This class implements the initial screen displayed to the user upon launching the application.
 **/
object WelcomeScreen : ScreenInterface {
    @Composable
    override fun displayScreen(onBackPressed: () -> Unit, onSelection: () -> Unit) {
        welcomeScreenContent()
    }
}

@Preview(showBackground = true)
@SuppressLint("ComposableNaming")
@Composable
fun welcomeScreenPreview() {
    welcomeScreenContent()
}

@SuppressLint("ComposableNaming")
@Composable
fun welcomeScreenContent() {

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

        drawText()

        drawlink(mActivity, context, clipboardManager)

        drawSwitch(isSwitchChecked, persistentStorage)

    }
}

@SuppressLint("ComposableNaming")
@Composable
private fun drawSwitch(
    isSwitchChecked: Boolean,
    persistentStorage: PersistentStorage
) {
    var isSwitchChecked1 = isSwitchChecked
    toggleWithText(
        text = stringResource(R.string.welcome_screen_on_off),
        isChecked = isSwitchChecked1,
        fontWeight = FontWeight.Medium,
        onCheckedChange = { isChecked ->
            isSwitchChecked1 = isChecked
            persistentStorage.saveStatus(isChecked)
            Log.d("WelcomeScreen", "Switch is now ${if (isChecked) "ON" else "OFF"}")
        }
    )
}

@SuppressLint("ComposableNaming")
@Composable
private fun drawlink(
    mActivity: Activity?,
    context: Context,
    clipboardManager: ClipboardManager
) {
    val docUrl = stringResource(R.string.e_foundation_docs_link) // K1ZFP TODO update
    Text(
        text = docUrl,
        fontSize = 15.sp,
        color = colorResource(foundation.e.elib.R.color.e_accent),
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
        style = TextStyle(textDecoration = TextDecoration.Underline),
        modifier =
            Modifier
                .padding(bottom = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            try {
                                val customTabsIntent =
                                    CustomTabsIntent.Builder().setShowTitle(true).build()
                                if (mActivity != null) {
                                    customTabsIntent.launchUrl(mActivity, docUrl.toUri())
                                }
                            } catch (e: IllegalArgumentException) {
                                Log.e("DocumentationLink", "Invalid URL: $docUrl", e)
                                fallback(docUrl, context)
                            } catch (e: ActivityNotFoundException) {
                                Log.e("DocumentationLink", "No browser found for URL: $docUrl", e)
                                fallback(docUrl, context)
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
}

@SuppressLint("ComposableNaming")
@Composable
private fun drawText() {
    Text(
        text = stringResource(R.string.welcome_screen_caption),
        color = colorResource(foundation.e.elib.R.color.e_primary_text_color),
        fontSize = 15.sp,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
        modifier = Modifier.padding(bottom = Dimens.SCREEN_PADDING / 2)
    )
}

private fun fallback(docUrl: String, context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, docUrl.toUri())
    context.startActivity(intent)
}
