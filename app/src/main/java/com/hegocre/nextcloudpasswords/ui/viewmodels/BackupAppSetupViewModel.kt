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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackupAppSetupViewModel(private val application: Application) : AndroidViewModel(application) {

    private val apiController: ApiController
        get() = ApiController.getInstance(application)
    private val preferencesManager: PreferencesManager
        get() = PreferencesManager.getInstance(application)

    private val _uiState = MutableStateFlow(BackupAppSetupUiState())
    val uiState = _uiState.asStateFlow()

    private val _response = MutableStateFlow<SetupResponse?>(null)
    val response = _response.asStateFlow()

    fun refreshAccounts() {
        val account = SsoAccount.getCurrentSingleSignOnAccount(application)

        if (account == null) {
            Log.d(TAG, "murena account is not available, leaving e2ee setup")
            finishWithResponse(SetupResponse.AccountUnavailable)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
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
                    _uiState.value = BackupAppSetupUiState(
                        isLoading = false,
                        accountName = account.name,
                    )
                }

                else -> finishWithResponse(SetupResponse.Failed)
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordState = if (password.length < MINIMUM_PASSWORD_LENGTH) {
                BackupAppSetupPasswordState.Empty
            } else {
                BackupAppSetupPasswordState.Unknown
            }
        )
    }

    fun submitPassword() {
        val passphrase = _uiState.value.password
        _uiState.value = _uiState.value.copy(
            passwordState = BackupAppSetupPasswordState.Checking
        )

        viewModelScope.launch {
            var responseState: SetupResponse? = null
            val hadSession = apiController.sessionOpen.value
            var openedTemporarySession = false
            val passwordResult = try {
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
                sessionOpened to BackupAppSetupPasswordState.Wrong
            } catch (_: PWDv1ChallengeMasterKeyNeededException) {
                false to BackupAppSetupPasswordState.Wrong
            } catch (_: PWDv1ChallengeMasterKeyInvalidException) {
                false to BackupAppSetupPasswordState.Wrong
            } catch (_: PWDv1ChallengePasswordException) {
                false to BackupAppSetupPasswordState.Wrong
            } catch (_: ClientDeauthorizedException) {
                Log.d(TAG, "please re-login again")
                responseState = SetupResponse.AccountUnavailable
                false to BackupAppSetupPasswordState.Unknown
            } finally {
                if (openedTemporarySession) {
                    apiController.clearSession()
                }
            }

            val (isCorrect, failureState) = passwordResult
            _uiState.value = _uiState.value.copy(
                passwordState = if (isCorrect) {
                    BackupAppSetupPasswordState.Correct
                } else {
                    failureState
                }
            )

            if (isCorrect) {
                Log.d(TAG, "setup completed, isSuccessful : true")
                finishWithResponse(SetupResponse.Success)
            } else {
                responseState?.let(::finishWithResponse)
            }
        }
    }

    fun finishWithResponse(response: SetupResponse) {
        _response.value = response
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
        val passwordState: BackupAppSetupPasswordState = BackupAppSetupPasswordState.Empty
    )

    enum class BackupAppSetupPasswordState {
        Unknown,
        Wrong,
        Correct,
        Empty,
        Checking
    }

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
