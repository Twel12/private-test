package com.hegocre.nextcloudpasswords.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.ui.components.E2eeMigrationDialog
import com.hegocre.nextcloudpasswords.ui.components.OutlinedTextFieldWithCaption
import com.hegocre.nextcloudpasswords.ui.migration.launchE2eeMigration
import com.hegocre.nextcloudpasswords.ui.viewmodels.BackupAppSetupViewModel
import foundation.e.data.SetupConsent
import foundation.e.data.SetupResponse
import foundation.e.elib.compose.components.ELargeTopAppBar
import foundation.e.elib.compose.theme.ETheme

@ExperimentalMaterial3Api
class BackupAppSetupActivity : ComponentActivity() {

    companion object {
        const val TAG = "BackupAppSetupActivity"
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

        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val response by viewModel.response.collectAsStateWithLifecycle()
            val showE2eeMigrationDialog by viewModel.showE2eeMigrationDialog.collectAsStateWithLifecycle()
            val ssoReauthenticationRequested by
                viewModel.ssoReauthenticationRequested.collectAsStateWithLifecycle()

            LaunchedEffect(response) {
                response?.let(::response)
            }

            LaunchedEffect(ssoReauthenticationRequested) {
                if (ssoReauthenticationRequested) {
                    viewModel.clearSsoReauthenticationRequest()
                    baseAutoLogin.start(forceSsoReauthentication = true)
                }
            }

            if (uiState.isLoading || uiState.accountName == null) {
                ETheme {
                    Box(
                        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(64.dp)
                        )
                    }
                }
            } else {
                MasterKeyScreen(
                    isCheckingPassword = uiState.isCheckingPassword,
                    isWrongPassword = uiState.isWrongPassword,
                    password = uiState.password,
                    canSubmit = uiState.canSubmitPassword,
                    onPasswordChange = viewModel::onPasswordChanged,
                    onSubmit = viewModel::submitPassword,
                    onBack = ::finishCanceled
                )
            }

            if (showE2eeMigrationDialog) {
                ETheme {
                    E2eeMigrationDialog(
                        onStartMigration = { launchE2eeMigration(viewModel) },
                        onCancel = ::finishCanceled
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumedAfterMigration()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        baseAutoLogin.onActivityResult(requestCode, resultCode, data)
    }

    @Preview
    @Composable
    private fun Demo() {
        MasterKeyScreen(
            isCheckingPassword = false,
            isWrongPassword = false,
            password = "",
            canSubmit = false,
            onPasswordChange = {},
            onSubmit = {},
            onBack = {})
    }

    @Composable
    private fun MasterKeyScreen(
        isCheckingPassword: Boolean,
        isWrongPassword: Boolean,
        password: String,
        canSubmit: Boolean,
        onPasswordChange: (String) -> Unit,
        onSubmit: () -> Unit,
        onBack: () -> Unit,
    ) {
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(isCheckingPassword) {
            if (!isCheckingPassword) {
                focusRequester.requestFocus()
            }
        }
        ETheme {
            BackHandler(onBack = onBack)

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
                topBar = {
                    ELargeTopAppBar(
                        expandedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
                        title = {}, navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.navigation_back)
                                )
                            }
                        },
                        windowInsets = WindowInsets.statusBars
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Button(
                                onClick = {
                                    onSubmit()
                                },
                                enabled = canSubmit,
                            ) {
                                Text(stringResource(R.string.backup_app_setup_cta))
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.backup_app_setup_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.description),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCheckingPassword) {
                            CircularProgressIndicator()
                        } else {
                            PasswordInputField(
                                isWrongPassword = isWrongPassword,
                                password = password,
                                onValueChange = onPasswordChange,
                                onSubmit = onSubmit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PasswordInputField(
        isWrongPassword: Boolean,
        password: String,
        onValueChange: (String) -> Unit,
        onSubmit: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

        OutlinedTextFieldWithCaption(
            text = password,
            onValueChange = onValueChange,
            errorText = if (isWrongPassword) {
                stringResource(R.string.backup_app_setup_password_error)
            } else {
                ""
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardType = KeyboardType.Password,
            label = stringResource(R.string.enter_password_hint),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.text_input_show_password_toggle)
                    )
                }
            },
            modifier = Modifier
                .then(modifier)
                .contentType(ContentType.Password),
            textFieldModifier = Modifier.fillMaxWidth(),
            onDone = { onSubmit() })
    }

    private fun response(response: SetupResponse) {
        viewModel.clearPasswordInput()
        val code = if (response == SetupResponse.Success) RESULT_OK else RESULT_CANCELED
        setResult(code, response.toExtra())
        finish()
    }

    private fun finishCanceled() {
        viewModel.clearPasswordInput()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun SetupResponse.toExtra() = Intent().apply {
        putExtra(SetupConsent.EXTRA_SETUP_RESPONSE, ordinal)
    }
}
