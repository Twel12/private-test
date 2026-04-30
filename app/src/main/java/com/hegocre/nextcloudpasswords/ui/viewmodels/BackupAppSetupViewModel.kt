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
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
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
    private val preferencesManager: PreferencesManager
        get() = PreferencesManager.getInstance(application)

    private val _uiState = MutableStateFlow(BackupAppSetupUiState())
    val uiState = _uiState.asStateFlow()

    private val _response = MutableStateFlow<SetupResponse?>(null)
    val response = _response.asStateFlow()

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
            when (restoreExistingE2eeState()) {
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
                }
            }
            if (sessionOpened) {
                preferencesManager.setMasterPassword(passphrase)
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
        val masterPassword = preferencesManager.getMasterPassword() ?: return null
        val hadSession = apiController.sessionOpen.value
        var openedTemporarySession = false
        return try {
            val isAvailable = if (hadSession) {
                apiController.restoreStoredKeychain(masterPassword) &&
                    apiController.isEndToEndEncryptionKeyAvailable()
            } else {
                apiController.openSession(masterPassword).also { sessionOpened ->
                    openedTemporarySession = sessionOpened
                } && apiController.isEndToEndEncryptionKeyAvailable()
            }
            if (isAvailable) {
                SetupResponse.Success
            } else {
                null
            }
        } catch (_: PWDv1ChallengeMasterKeyNeededException) {
            preferencesManager.setMasterPassword(null)
            null
        } catch (_: PWDv1ChallengeMasterKeyInvalidException) {
            preferencesManager.setMasterPassword(null)
            null
        } catch (_: PWDv1ChallengePasswordException) {
            preferencesManager.setMasterPassword(null)
            null
        } catch (_: ClientDeauthorizedException) {
            SetupResponse.AccountUnavailable
        } finally {
            if (openedTemporarySession) {
                apiController.clearSession()
            }
        }
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
