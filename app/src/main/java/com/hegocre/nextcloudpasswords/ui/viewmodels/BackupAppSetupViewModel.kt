package com.hegocre.nextcloudpasswords.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.exceptions.ClientDeauthorizedException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.data.user.UserException
import com.hegocre.nextcloudpasswords.ui.migration.E2eeMigrationFlowHandler
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.SecureMasterPasswordStore
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import foundation.e.data.SetupResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BackupAppSetupViewModel(private val application: Application) :
    AndroidViewModel(application), E2eeMigrationFlowHandler {

    private val _uiState = MutableStateFlow(BackupAppSetupUiState())
    val uiState = _uiState.asStateFlow()

    private val _response = MutableStateFlow<SetupResponse?>(null)
    val response = _response.asStateFlow()

    private val _ssoReauthenticationRequested = MutableStateFlow(false)
    val ssoReauthenticationRequested = _ssoReauthenticationRequested.asStateFlow()

    private val migrationEligible = MutableStateFlow(false)
    private val endToEndEncryptionEnabled = MutableStateFlow<Boolean?>(null)

    private val e2eeMigrationCoordinator = E2eeMigrationCoordinator(
        viewModelScope = viewModelScope,
        endToEndEncryptionEnabled = endToEndEncryptionEnabled,
        serverUrlProvider = { runCatching { UserController.getInstance(application).getServer().url }.getOrNull() },
        migrationEligible = migrationEligible
    )

    val showE2eeMigrationDialog = e2eeMigrationCoordinator.showMigrationDialog

    private var refreshAccountsJob: Job? = null
    private var submitPasswordJob: Job? = null
    private var apiStateJob: Job? = null
    private var passwordSubmissionVersion = 0L
    private val refreshAccountsMutex = Mutex()

    fun refreshAccounts() {
        if (refreshAccountsJob?.isActive == true) {
            return
        }

        refreshAccountsJob = viewModelScope.launch {
            refreshAccountsInternal()
        }
    }

    private suspend fun refreshAccountsInternal() {
        refreshAccountsMutex.withLock {
            migrationEligible.value = false

            val account = SsoAccount.getCurrentSingleSignOnAccount(application)

            if (account == null) {
                Log.d(TAG, "murena account is not available, leaving e2ee setup")
                finishWithResponse(SetupResponse.AccountUnavailable)
            } else {
                _uiState.update { it.copy(isLoading = true) }
                val apiController = getApiControllerOrFinish() ?: return
                observeApiState(apiController)
                val existingE2eeState = restoreExistingE2eeState()
                if (!_ssoReauthenticationRequested.value) {
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
                            val requiresMigration = shouldRunE2eeMigration()

                            if (!_ssoReauthenticationRequested.value) {
                                if (requiresMigration) {
                                    Log.d(TAG, "e2ee is disabled, starting migration flow")
                                    migrationEligible.value = true
                                } else {
                                    Log.d(TAG, "e2ee key is not available asking user for the key")
                                }

                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        accountName = account.name,
                                    )
                                }
                            }
                        }

                        else -> finishWithResponse(SetupResponse.Failed)
                    }
                }
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

    override fun prepareE2eeMigrationUri() = e2eeMigrationCoordinator.prepareMigrationUri()

    override fun onE2eeMigrationLaunched() {
        e2eeMigrationCoordinator.onMigrationLaunched()
    }

    override fun onE2eeMigrationLaunchFailed() {
        e2eeMigrationCoordinator.onMigrationLaunchFailed()
    }

    override fun onAppResumedAfterMigration() {
        refreshAccountsJob = viewModelScope.launch {
            e2eeMigrationCoordinator.onAppResumedAfterMigration {
                refreshAccountsInternal()
            }
        }
    }

    fun clearSsoReauthenticationRequest() {
        getApiControllerOrFinish()?.clearSsoReauthenticationRequired()
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
        val apiController = getApiControllerOrFinish() ?: return PasswordCheckResult(
            isCorrect = false,
            response = SetupResponse.AccountUnavailable,
        )
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
        val apiController = getApiControllerOrFinish() ?: return SetupResponse.AccountUnavailable
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

    private suspend fun shouldRunE2eeMigration(): Boolean {
        var openedTemporarySession = false
        val apiController = getApiControllerOrFinish() ?: return false
        return try {
            val sessionOpened = apiController.openSession(null).also { sessionOpened ->
                openedTemporarySession = sessionOpened
            }

            when {
                !sessionOpened && apiController.isSsoReauthenticationRequired() -> {
                    requestSsoReauthentication()
                    false
                }

                sessionOpened -> apiController.endToEndEncryptionEnabled.value == false
                else -> false
            }
        } catch (_: PWDv1ChallengeMasterKeyNeededException) {
            Log.d(TAG, "migration probe hit master-key-needed challenge")
            false
        } catch (_: PWDv1ChallengeMasterKeyInvalidException) {
            Log.d(TAG, "migration probe hit invalid-master-key challenge")
            false
        } catch (_: PWDv1ChallengePasswordException) {
            Log.d(TAG, "migration probe hit password challenge")
            false
        } finally {
            if (openedTemporarySession) {
                apiController.clearSession()
            }
        }
    }

    private fun restoreExistingSessionE2eeState(masterPassword: String): SetupResponse? {
        val apiController = getApiControllerOrFinish() ?: return SetupResponse.AccountUnavailable
        val keychainRestored = apiController.restoreStoredKeychain(masterPassword)
        return if (keychainRestored) {
            getAvailableE2eeResponse()
        } else {
            null
        }
    }

    private suspend fun restoreTemporarySessionE2eeState(masterPassword: String): SetupResponse? {
        var openedTemporarySession = false
        val apiController = getApiControllerOrFinish() ?: return SetupResponse.AccountUnavailable
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
        val apiController = getApiControllerOrFinish() ?: return SetupResponse.AccountUnavailable
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

    private fun observeApiState(apiController: ApiController) {
        if (apiStateJob?.isActive == true) return

        apiStateJob = viewModelScope.launch {
            apiController.endToEndEncryptionEnabled.collect { enabled ->
                endToEndEncryptionEnabled.value = enabled
            }
        }
    }

    private fun getApiControllerOrFinish(): ApiController? {
        return try {
            ApiController.getInstance(application)
        } catch (exception: UserException) {
            Log.d(TAG, "api controller is unavailable during e2ee setup", exception)
            finishWithResponse(SetupResponse.AccountUnavailable)
            null
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
