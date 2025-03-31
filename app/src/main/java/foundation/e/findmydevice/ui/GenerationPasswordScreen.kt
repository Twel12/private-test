package foundation.e.findmydevice.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import foundation.e.findmydevice.util.PasswordGenerator
import foundation.e.findmydevice.storage.PersistentStorage
import foundation.e.findmydevice.R
import foundation.e.findmydevice.ui.buttons.actionColor
import foundation.e.findmydevice.ui.buttons.buttonColor
import foundation.e.findmydevice.util.Dimens
import kotlinx.coroutines.launch

import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import foundation.e.findmydevice.activity.FindMyDeviceActivity
import foundation.e.findmydevice.activity.FindMyDeviceActivity.Companion.TAG
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * GenerationPasswordScreen
 *
 * This class implements a screen within the application's user interface that
 * is responsible for generating and displaying a new, random password.
 **/
object GenerationPasswordScreen {

    internal const val CODE_COLOR = 0xFF1A9E24
    internal const val TAG = "GenerationPasswordScreen"

    @SuppressLint("ComposableNaming")
    @Composable
    fun displayScreen(
        onBackPressed: () -> Unit,
        onSelection: () -> Unit,
        findMyDeviceActivity: FindMyDeviceActivity?
    ) {
        BackHandler(onBack = { onBackPressed() })
        generatePasswordScreenContent(onSelection, findMyDeviceActivity)
    }
}

@Preview(showBackground = true)
@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenPreview() {
    generatePasswordScreenContent(onSelection = {}, findMyDeviceActivity = null)
}

suspend fun showBiometricPromptAsync(findMyDeviceActivity: FindMyDeviceActivity): Boolean =
    suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(findMyDeviceActivity)

        val biometricPrompt = BiometricPrompt(
            findMyDeviceActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    continuation.resume(false)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    continuation.resume(true)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(findMyDeviceActivity.getString(R.string.main_security_title))
            .setSubtitle(findMyDeviceActivity.getString(R.string.main_security_description))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)

        continuation.invokeOnCancellation {
            biometricPrompt.cancelAuthentication()
        }
    }

fun checkSecurity(findMyDeviceActivity: FindMyDeviceActivity): Boolean {
    val biometricManager = BiometricManager.from(findMyDeviceActivity)

    return when (biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {

        BiometricManager.BIOMETRIC_SUCCESS -> true

        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false

        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            Log.d(TAG, "Biometric hardware is currently unavailable")
            false
        }

        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
            Log.d(TAG, "Biometric security unsupported or unknown")
            false
        }

        else -> {
            Log.d(TAG, "Unknown error when checking biometric capability")
            false
        }
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun generatePasswordScreenContent(onSelection: () -> Unit,
                                  findMyDeviceActivity: FindMyDeviceActivity? = null) {
    val context = LocalContext.current
    val persistentStorage = PersistentStorage(context)
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var isSwitchChecked by remember { mutableStateOf(false) }

    var hasSecurity by remember { mutableStateOf(false) }


    LaunchedEffect(key1 = true) {
        scope.launch {
            var savedPassword = persistentStorage.getPassword()
            if (savedPassword.isNullOrEmpty()) {
                val newPassword = PasswordGenerator().generatePassword()
                persistentStorage.savePassword(newPassword)
                savedPassword = newPassword
            }
            currentPassword = savedPassword
            val savedStatus = persistentStorage.getStatus()
            isSwitchChecked = savedStatus

            // Step2 Existing feature allowing to use biometric authentication
            hasSecurity = false //checkSecurity(findMyDeviceActivity!!)
        }
    }

    @Composable
    fun displayCharacter(char: Char) {
        Text(
            text = char.toString(),
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.15.sp,
            textAlign = TextAlign.Center,
            color = Color(GenerationPasswordScreen.CODE_COLOR),
        )
    }

    @Composable
    fun displayPassword() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 25.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            currentPassword.forEach { char ->
                displayCharacter(char = char)
            }
        }
    }

    @Composable
    fun generateNewCode(findMyDeviceActivity: FindMyDeviceActivity) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    scope.launch {
                        var performSave = true
                        if (hasSecurity) {
                            performSave = showBiometricPromptAsync(findMyDeviceActivity)
                        }
                        if (performSave) {
                            val newPassword = PasswordGenerator().generatePassword()
                            currentPassword = newPassword
                            persistentStorage.savePassword(currentPassword)
                        } else {
                            Log.d(TAG, "Password not saved")
                        }
                    }
                },
                colors = actionColor()
            ) {
                Text(text = stringResource(id = R.string.generate_new_password))
            }
        }
    }

    @Composable
    fun displayTestProcedure() {
        val grayColor = Color.Gray
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = grayColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.find_my_phone_test),
                contentDescription = "",
                tint = colorResource(foundation.e.elib.R.color.e_icon_color),
                modifier = Modifier.size(30.dp)

            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.password_test_procedure),
                color = grayColor,
                fontSize = 14.sp
            )
        }
    }

    @Composable
    fun displayNextButton() {

        val contentResolver = context.getContentResolver()
        val isProvisioned = Settings.Global.getInt(contentResolver,
            Settings.Global.DEVICE_PROVISIONED) == 1;

        if (!isProvisioned) {
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
                    colors = buttonColor()
                ) {
                    Text(text = stringResource(id = R.string.password_next))
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {

        Text(text = stringResource(id = R.string.welcome_screen_intro_1),
            style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        Text(text = stringResource(id = R.string.welcome_screen_intro_2),
            style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        Text(text = stringResource(id = R.string.current_password),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayPassword()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        generateNewCode(findMyDeviceActivity = findMyDeviceActivity!!)

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayTestProcedure()

        Spacer(modifier = Modifier.height(Dimens.TEXT_SPACING))

        displayNextButton()
    }
}
