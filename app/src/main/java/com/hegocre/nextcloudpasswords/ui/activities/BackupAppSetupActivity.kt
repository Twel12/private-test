package com.hegocre.nextcloudpasswords.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.ui.theme.NextcloudPasswordsTheme
import com.hegocre.nextcloudpasswords.ui.viewmodels.BackupAppSetupViewModel
import com.hegocre.nextcloudpasswords.ui.viewmodels.BackupAppSetupViewModel.BackupAppSetupPasswordState
import foundation.e.data.SetupConsent
import foundation.e.data.SetupResponse

class BackupAppSetupActivity : ComponentActivity() {

    companion object {
        const val TAG = "BackupAppSetupActivity"
        const val MINIMUM_PASSWORD_LENGTH = 12
        const val HALF_SCREEN_FRACTION = 0.5f
    }

    private val viewModel: BackupAppSetupViewModel by viewModels {
        BackupAppSetupViewModel.factory(application)
    }

    private val baseAutoLogin by lazy {
        object : BaseAutoLogin(this@BackupAppSetupActivity) {
            override fun accountExist() {
                viewModel.refreshAccounts()
            }

            override fun onLoginSuccess() {
                viewModel.refreshAccounts()
            }

            override fun signatureError() {
                viewModel.finishWithResponse(SetupResponse.Failed)
            }

            override fun accountUnavailable() {
                Log.d(TAG, "murena account is not available, leaving e2ee setup")
                viewModel.finishWithResponse(SetupResponse.AccountUnavailable)
            }

            override fun syncDisabled() {
                viewModel.finishWithResponse(SetupResponse.Failed)
            }

            override fun ssoFailed() {
                viewModel.finishWithResponse(SetupResponse.Failed)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        baseAutoLogin.start()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val response by viewModel.response.collectAsState()

            LaunchedEffect(response) {
                response?.let(::response)
            }

            if (uiState.isLoading || uiState.accountName == null) {
                NextcloudPasswordsTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(64.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            } else {
                MasterKeyScreen(
                    name = uiState.accountName.orEmpty(),
                    state = uiState.passwordState,
                    password = uiState.password,
                    onPasswordChange = viewModel::onPasswordChanged,
                    onSubmit = viewModel::submitPassword
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        baseAutoLogin.onActivityResult(requestCode, resultCode, data)
    }

    @Preview
    @Composable
    private fun Demo() {
        MasterKeyScreen(
            name = "user",
            state = BackupAppSetupPasswordState.Empty,
            password = "",
            onPasswordChange = {},
            onSubmit = {}
        )
    }

    @Composable
    private fun MasterKeyScreen(
        name: String,
        state: BackupAppSetupPasswordState,
        password: String,
        onPasswordChange: (String) -> Unit,
        onSubmit: () -> Unit,
    ) {
        NextcloudPasswordsTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                            .fillMaxHeight(HALF_SCREEN_FRACTION)
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_user, name.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        )
                    }

                    PasswordInputField(
                        state,
                        password,
                        onValueChange = onPasswordChange,
                        onSubmit = onSubmit
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    SubmitButton(state = state, onSubmit = onSubmit)
                }
            }

        }
    }

    @Composable
    private fun PasswordInputField(
        state: BackupAppSetupPasswordState,
        password: String,
        onValueChange: (String) -> Unit,
        onSubmit: () -> Unit
    ) {
        val shouldEnable = listOf(
            BackupAppSetupPasswordState.Empty,
            BackupAppSetupPasswordState.Unknown,
            BackupAppSetupPasswordState.Wrong
        ).contains(state)
        var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

        OutlinedTextField(
            value = password,
            enabled = shouldEnable,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.enter_password_hint)) },
            singleLine = true,
            isError = state == BackupAppSetupPasswordState.Wrong,
            visualTransformation =
                if (isPasswordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (state == BackupAppSetupPasswordState.Unknown) {
                        onSubmit()
                    }
                }
            ),
            trailingIcon = {
                val image = if (isPasswordVisible)
                    Icons.Default.VisibilityOff
                else
                    Icons.Default.Visibility

                val description = if (isPasswordVisible)
                    stringResource(R.string.hide_password_hint)
                else
                    stringResource(R.string.show_password_hint)

                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            }
        )
    }

    @Composable
    private fun SubmitButton(state: BackupAppSetupPasswordState, onSubmit: () -> Unit) {
        Button(
            onClick = {
                onSubmit()
            },
            enabled = state == BackupAppSetupPasswordState.Unknown,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state == BackupAppSetupPasswordState.Checking) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(stringResource(R.string.submit))
            }
        }
    }

    private fun response(response: SetupResponse) {
        val code = if (response == SetupResponse.Success) RESULT_OK else RESULT_CANCELED
        setResult(code, response.toExtra())
        finish()
    }

    private fun SetupResponse.toExtra() = Intent().apply {
        putExtra(SetupConsent.EXTRA_SETUP_RESPONSE, ordinal)
    }
}
