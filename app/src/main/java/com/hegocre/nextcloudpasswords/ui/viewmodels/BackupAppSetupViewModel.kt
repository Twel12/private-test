package com.hegocre.nextcloudpasswords.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.exceptions.ClientDeauthorizedException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.SecureMasterPasswordStore
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import foundation.e.data.SetupResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BackupAppSetupViewModel(private val application: Application) : AndroidViewModel(application) {

    private val apiController: ApiController
        get() = ApiController.getInstance(application)

    private val _uiState = MutableStateFlow(BackupAppSetupUiState())
    val uiState = _uiState.asStateFlow()

    private val _response = MutableStateFlow<SetupResponse?>(null)
    val response = _response.asStateFlow()

    private val _ssoReauthenticationRequested = MutableStateFlow(false)
    val ssoReauthenticationRequested = _ssoReauthenticationRequested.asStateFlow()

    private var refreshAccountsJob: Job? = null
    private var submitPasswordJob: Job? = null
    private var passwordSubmissionVersion = 0L

    fun refreshAccounts() {
        if (refreshAccountsJob?.isActive == true) {
            return
        }

        val account = SsoAccount.getCurrentSingleSignOnAccount(application)

        if (account == null) {
            Log.d(TAG, "murena account is not available, leaving e2ee setup")
            finishWithResponse(SetupResponse.AccountUnavailable)
            return
        }

        refreshAccountsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val existingE2eeState = restoreExistingE2eeState()
            if (_ssoReauthenticationRequested.value) {
                return@launch
            }
            when (existingE2eeState) {
                SetupResponse.Success -> {
                    Log.d(TAG, "account and e2ee is already configured.")
                    finishWithResponse(SetupResponse.Success)
                }

                SetupResponse.AccountUnavailable -> {
                    Log.d(TAG, "murena account is not available, leaving e2ee setup")
                    finishWithResponse(SetupResponse.AccountUnavailable)
                }

                null -> {
                    Log.d(TAG, "e2ee key is not available asking user for the key")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            accountName = account.name,
                        )
                    }
                }

                else -> finishWithResponse(SetupResponse.Failed)
            }
        }
    }

    fun onPasswordChanged(password: String) {
        submitPasswordJob?.cancel()
        passwordSubmissionVersion++
        _uiState.update {
            it.copy(
                password = password,
                isCheckingPassword = false,
                isWrongPassword = false,
            )
        }
    }

    fun clearPasswordInput() {
        submitPasswordJob?.cancel()
        passwordSubmissionVersion++
        _uiState.update {
            it.copy(
                password = "",
                isCheckingPassword = false,
                isWrongPassword = false,
            )
        }
    }

    fun submitPassword() {
        val currentUiState = _uiState.value
        if (!currentUiState.canSubmitPassword) return

        val passphrase = currentUiState.password
        val submissionVersion = ++passwordSubmissionVersion

        submitPasswordJob?.cancel()
        _uiState.update {
            it.copy(
                isCheckingPassword = true,
                isWrongPassword = false,
            )
        }

        submitPasswordJob = viewModelScope.launch {
            val passwordCheckResult = verifySubmittedPassword(passphrase)

            if (
                !isActive ||
                submissionVersion != passwordSubmissionVersion ||
                _uiState.value.password != passphrase
            ) {
                return@launch
            }

            if (_ssoReauthenticationRequested.value) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    isCheckingPassword = false,
                    isWrongPassword = !passwordCheckResult.isCorrect && passwordCheckResult.response == null,
                )
            }

            if (passwordCheckResult.isCorrect) {
                Log.d(TAG, "setup completed, isSuccessful : true")
                finishWithResponse(SetupResponse.Success)
            } else {
                passwordCheckResult.response?.let(::finishWithResponse)
            }
        }
    }

    fun finishWithResponse(response: SetupResponse) {
        _response.value = response
    }

    fun clearSsoReauthenticationRequest() {
        apiController.clearSsoReauthenticationRequired()
        _ssoReauthenticationRequested.value = false
    }

    private fun requestSsoReauthentication() {
        Log.d(TAG, "stale SSO token, requesting reauthentication")
        _uiState.update {
            it.copy(
                isLoading = true,
                isCheckingPassword = false,
                isWrongPassword = false,
            )
        }
        _ssoReauthenticationRequested.value = true
    }

    private suspend fun verifySubmittedPassword(passphrase: String): PasswordCheckResult {
        var response: SetupResponse? = null
        val hadSession = apiController.sessionOpen.value
        var openedTemporarySession = false

        val isCorrect = try {
            val sessionOpened = if (hadSession) {
                apiController.restoreStoredKeychain(passphrase)
            } else {
                apiController.openSession(passphrase).also { sessionOpened ->
                    openedTemporarySession = sessionOpened
                    if (!sessionOpened && apiController.isSsoReauthenticationRequired()) {
                        requestSsoReauthentication()
                    }
                }
            }
            if (sessionOpened) {
                MasterPasswordMemoryStore.set(passphrase)
            }
            sessionOpened
        } catch (_: PWDv1ChallengeMasterKeyNeededException) {
            false
        } catch (_: PWDv1ChallengeMasterKeyInvalidException) {
            false
        } catch (_: PWDv1ChallengePasswordException) {
            false
        } catch (_: ClientDeauthorizedException) {
            Log.d(TAG, "please re-login again")
            response = SetupResponse.AccountUnavailable
            false
        } finally {
            if (openedTemporarySession) {
                apiController.clearSession()
            }
        }

        return PasswordCheckResult(
            isCorrect = isCorrect,
            response = response,
        )
    }

    private suspend fun restoreExistingE2eeState(): SetupResponse? {
        val masterPassword = MasterPasswordMemoryStore.get() ?: return null
        return try {
            if (apiController.sessionOpen.value) {
                restoreExistingSessionE2eeState(masterPassword)
            } else {
                restoreTemporarySessionE2eeState(masterPassword)
            }
        } catch (_: PWDv1ChallengeMasterKeyNeededException) {
            clearMasterPasswordState()
            null
        } catch (_: PWDv1ChallengeMasterKeyInvalidException) {
            clearMasterPasswordState()
            null
        } catch (_: PWDv1ChallengePasswordException) {
            clearMasterPasswordState()
            null
        } catch (_: ClientDeauthorizedException) {
            SetupResponse.AccountUnavailable
        }
    }

    private fun restoreExistingSessionE2eeState(masterPassword: String): SetupResponse? {
        val keychainRestored = apiController.restoreStoredKeychain(masterPassword)
        return if (keychainRestored) {
            getAvailableE2eeResponse()
        } else {
            null
        }
    }

    private suspend fun restoreTemporarySessionE2eeState(masterPassword: String): SetupResponse? {
        var openedTemporarySession = false
        return try {
            val sessionOpened = apiController.openSession(masterPassword).also { sessionOpened ->
                openedTemporarySession = sessionOpened
            }

            when {
                !sessionOpened && apiController.isSsoReauthenticationRequired() -> {
                    requestSsoReauthentication()
                    null
                }

                sessionOpened -> getAvailableE2eeResponse()
                else -> null
            }
        } finally {
            if (openedTemporarySession) {
                apiController.clearSession()
            }
        }
    }

    private fun getAvailableE2eeResponse(): SetupResponse? {
        return if (apiController.isEndToEndEncryptionKeyAvailable()) {
            SetupResponse.Success
        } else {
            null
        }
    }

    private fun clearMasterPasswordState() {
        MasterPasswordMemoryStore.clear()
        SecureMasterPasswordStore(application).clear()
    }

    data class BackupAppSetupUiState(
        val isLoading: Boolean = true,
        val accountName: String? = null,
        val password: String = "",
        val isCheckingPassword: Boolean = false,
        val isWrongPassword: Boolean = false,
    ) {
        val canSubmitPassword: Boolean
            get() = !isCheckingPassword && password.length >= MINIMUM_PASSWORD_LENGTH
    }

    private data class PasswordCheckResult(
        val isCorrect: Boolean,
        val response: SetupResponse?,
    )

    companion object {
        const val TAG = "BackupAppSetupViewModel"
        const val MINIMUM_PASSWORD_LENGTH = 8

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    require(modelClass == BackupAppSetupViewModel::class.java)
                    @Suppress("UNCHECKED_CAST")
                    return BackupAppSetupViewModel(application) as T
                }
            }
    }
}
