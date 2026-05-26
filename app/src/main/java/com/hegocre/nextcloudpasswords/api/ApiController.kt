package com.hegocre.nextcloudpasswords.api

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkManager
import com.hegocre.nextcloudpasswords.backupApp.BackupAppPassword
import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import com.hegocre.nextcloudpasswords.api.encryption.PWDv1Challenge
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.SsoReauthenticationRequiredException
import com.hegocre.nextcloudpasswords.data.folder.DeletedFolder
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.folder.NewFolder
import com.hegocre.nextcloudpasswords.data.folder.UpdatedFolder
import com.hegocre.nextcloudpasswords.data.password.DeletedPassword
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.services.keepalive.KeepAliveWorker
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import com.hegocre.nextcloudpasswords.utils.Result
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import com.hegocre.nextcloudpasswords.utils.encryptValue
import com.hegocre.nextcloudpasswords.utils.sha1Hash
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Class with methods used to interact with [the API](https://git.mdns.eu/nextcloud/passwords/-/wikis/Developers/Api)
 * classes. This is a Singleton class and will have only one instance.
 *
 * @param context Context of the application.
 */
class ApiController private constructor(context: Context) {
    private val context = context.applicationContext

    private val server = UserController.getInstance(context).getServer()

    private val preferencesManager = PreferencesManager.getInstance(context)

    private val passwordsApi = PasswordsApi.getInstance(server)
    private val foldersApi = FoldersApi.getInstance(server)
    private val sessionApi = SessionApi.getInstance(server)
    private val serviceApi = ServiceApi.getInstance(server)
    private val settingsApi = SettingsApi.getInstance(server)

    private var sessionCode: String? = null
    private var sessionVersion = 0L

    val csEv1Keychain = MutableLiveData<CSEv1Keychain?>(null)

    val serverSettings = MutableLiveData(
        preferencesManager.getServerSettings()
    )

    private val _endToEndEncryptionEnabled = MutableStateFlow<Boolean?>(null)
    val endToEndEncryptionEnabled: StateFlow<Boolean?>
        get() = _endToEndEncryptionEnabled.asStateFlow()

    private val _ssoReauthenticationRequired = MutableStateFlow(false)
    val ssoReauthenticationRequired: StateFlow<Boolean>
        get() = _ssoReauthenticationRequired.asStateFlow()

    private val _sessionOpen = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean>
        get() = _sessionOpen.asStateFlow()

    private val workManager = WorkManager.getInstance(context)

    class SessionLease private constructor(
        private val apiController: ApiController,
        private val sessionIdentity: SessionIdentity,
        private val clearStoredKeychainOnClose: Boolean,
    ) {
        suspend fun close(): Boolean {
            return apiController.closeSessionIfCurrent(
                sessionIdentity = sessionIdentity,
                clearStoredKeychain = clearStoredKeychainOnClose,
            )
        }

        suspend fun <T> use(
            onCloseFailed: () -> Unit = {},
            block: suspend () -> T,
        ): T {
            return try {
                block()
            } finally {
                if (!close()) {
                    onCloseFailed()
                }
            }
        }

        companion object {
            internal fun create(
                apiController: ApiController,
                sessionIdentity: SessionIdentity,
                clearStoredKeychainOnClose: Boolean,
            ): SessionLease {
                return SessionLease(
                    apiController = apiController,
                    sessionIdentity = sessionIdentity,
                    clearStoredKeychainOnClose = clearStoredKeychainOnClose,
                )
            }
        }
    }

    sealed interface TemporarySessionResult {
        data class Opened(val lease: SessionLease) : TemporarySessionResult
        data object AlreadyOpen : TemporarySessionResult
        data object Failed : TemporarySessionResult
    }

    internal data class SessionIdentity(
        val sessionCode: String?,
        val sessionVersion: Long,
    )

    private data class OpenSessionResult(
        val opened: Boolean,
        val sessionIdentity: SessionIdentity? = null,
    )

    init {
        val currentAccount = SsoAccount.getCurrentSingleSignOnAccount(context)

        if (currentAccount != null) {
            OkHttpRequestInterface.useSso(context, currentAccount)
        } else {
            OkHttpRequestInterface.useBasic(preferencesManager.getSkipCertificateValidation())
        }

        decryptCSEv1Keychain(
            preferencesManager.getCSEv1Keychain(),
            MasterPasswordMemoryStore.get()
        )?.let {
            csEv1Keychain.postValue(it)
        }

        CoroutineScope(Dispatchers.IO).launch {
            var result = settingsApi.get()
            while (result !is Result.Success) {
                if (result is Result.Error && result.code == Error.SSO_REAUTHENTICATION_REQUIRED) {
                    Timber.e("SSO re-authentication required")
                    requireSsoReauthentication()
                    return@launch
                }
                Log.e("ServerSettings", "Error getting server settings")
                delay(5000L)
                result = settingsApi.get()
            }
            Log.i("ServerSettings", "Got server settings")
            val settings = result.data
            serverSettings.postValue(settings)
            preferencesManager.setServerSettings(settings)
            preferencesManager.setInstanceColor(settings.themeColorPrimary)
        }

    }

    fun requireSsoReauthentication() {
        clearSessionState()
        SingleAccountHelper.commitCurrentAccount(context, "")
        AccountImporter.clearAllAuthTokens(context)
        _ssoReauthenticationRequired.value = true
    }

    fun isSsoReauthenticationRequired(): Boolean {
        return _ssoReauthenticationRequired.value
    }

    fun clearSsoReauthenticationRequired() {
        _ssoReauthenticationRequired.value = false
    }

    private fun currentSessionIdentity(): SessionIdentity? {
        return if (sessionOpen.value) {
            SessionIdentity(
                sessionCode = sessionCode,
                sessionVersion = sessionVersion,
            )
        } else {
            null
        }
    }

    private fun Result<*>.consumeSsoReauthError(): Boolean {
        val isSsoReauthError =
            this is Result.Error && code == Error.SSO_REAUTHENTICATION_REQUIRED
        if (isSsoReauthError) {
            requireSsoReauthentication()
        }
        return isSsoReauthError
    }

    private fun decryptCSEv1Keychain(
        encryptedData: String?,
        masterPassword: String?
    ): CSEv1Keychain? = try {
        encryptedData?.let { encryptedCSEv1Keychain ->
            masterPassword?.let { masterPassword ->
                val decryptedCsEv1KeychainJson = CSEv1Keychain.decryptJson(
                    encryptedCSEv1Keychain,
                    masterPassword
                )
                CSEv1Keychain.fromJson(decryptedCsEv1KeychainJson)
            }
        }
    } catch (_: Exception) {
        null
    }

    fun restoreStoredKeychain(masterPassword: String?): Boolean {
        val keychain = decryptCSEv1Keychain(
            preferencesManager.getCSEv1Keychain(),
            masterPassword
        ) ?: return false
        csEv1Keychain.postValue(keychain)
        return true
    }

    /**
     * Requests and opens a session via the [SessionApi] class.
     *
     * @param masterPassword Master password to request the session, if provided, and not needed
     * if no CSE used.
     * @return A boolean indicating if the session was successfully opened.
     * @throws PWDv1ChallengeMasterKeyNeededException If there is no master key provided, but one is
     * needed.
     * @throws PWDv1ChallengeMasterKeyInvalidException If a master key was provided, but is not valid.
     */
    @Throws(
        PWDv1ChallengeMasterKeyNeededException::class,
        PWDv1ChallengeMasterKeyInvalidException::class
    )
    suspend fun openSession(masterPassword: String?): Boolean {
        return openSessionForResult(masterPassword).opened
    }

    suspend fun openTemporarySession(
        masterPassword: String?,
        clearStoredKeychainOnClose: Boolean = true,
    ): TemporarySessionResult {
        if (sessionOpen.value) return TemporarySessionResult.AlreadyOpen
        val sessionResult = openSessionForResult(masterPassword)
        return if (sessionResult.opened) {
            val sessionIdentity = sessionResult.sessionIdentity
                ?: return TemporarySessionResult.Failed
            TemporarySessionResult.Opened(
                lease = SessionLease.create(
                    apiController = this,
                    sessionIdentity = sessionIdentity,
                    clearStoredKeychainOnClose = clearStoredKeychainOnClose,
                )
            )
        } else {
            TemporarySessionResult.Failed
        }
    }

    private suspend fun openSessionForResult(
        masterPassword: String?
    ): OpenSessionResult = withContext(Dispatchers.Default) {
        decryptCSEv1Keychain(
            preferencesManager.getCSEv1Keychain(),
            masterPassword
        )?.let {
            csEv1Keychain.postValue(it)
        }

        val challenge = getSessionChallenge(masterPassword)
            ?: return@withContext OpenSessionResult(opened = false)

        val secret = when (val secretResult = challenge.solve(masterPassword)) {
            is Result.Success -> secretResult.data
            is Result.Error -> return@withContext handleSecretError(secretResult.code)
        }

        val openedSessionRequest = sessionApi.openSession(secret)
        val openedSession = getOpenedSession(openedSessionRequest)
            ?: return@withContext OpenSessionResult(opened = false)

        return@withContext finishOpenSession(openedSession, masterPassword)
    }

    private suspend fun getSessionChallenge(masterPassword: String?): PWDv1Challenge? {
        return when (val requestResult = sessionApi.requestSession()) {
            is Result.Success -> {
                // E2EE state is derived from the session challenge: 3 salts -> CSE enabled.
                _endToEndEncryptionEnabled.value =
                    requestResult.data.salts.size == CSE_SALT_COUNT
                requestResult.data
            }

            is Result.Error -> handleRequestSessionError(requestResult.code, masterPassword)
        }
    }

    private fun handleRequestSessionError(
        code: Int,
        masterPassword: String?,
    ): PWDv1Challenge? {
        if (code == Error.SSO_REAUTHENTICATION_REQUIRED) {
            requireSsoReauthentication()
            return null
        }

        restoreCachedKeychain(masterPassword)
        logRequestSessionError(code)
        return null
    }

    private fun restoreCachedKeychain(masterPassword: String?) {
        preferencesManager.getCSEv1Keychain()?.let { cachedKeychain ->
            if (masterPassword == null) {
                throw PWDv1ChallengeMasterKeyNeededException()
            }

            decryptCSEv1Keychain(cachedKeychain, masterPassword)?.let {
                csEv1Keychain.postValue(it)
            } ?: throw PWDv1ChallengeMasterKeyInvalidException()
        }
    }

    private suspend fun handleSecretError(code: Int): OpenSessionResult {
        return if (code == Error.API_NO_CSE) {
            openSessionWithoutCse()
        } else {
            OpenSessionResult(opened = false)
        }
    }

    private suspend fun openSessionWithoutCse(): OpenSessionResult {
        clearSessionState()
        preferencesManager.setCSEv1Keychain(null)
        sessionVersion++
        val sessionIdentity = SessionIdentity(
            sessionCode = null,
            sessionVersion = sessionVersion,
        )
        _sessionOpen.emit(true)
        return OpenSessionResult(
            opened = true,
            sessionIdentity = sessionIdentity,
        )
    }

    private fun getOpenedSession(
        openedSessionRequest: Result<Pair<String, String>>
    ): Pair<String, String>? {
        return when (openedSessionRequest) {
            is Result.Success -> openedSessionRequest.data
            is Result.Error -> handleOpenSessionError(openedSessionRequest.code)
        }
    }

    private fun handleOpenSessionError(code: Int): Pair<String, String>? {
        if (code == Error.SSO_REAUTHENTICATION_REQUIRED) {
            requireSsoReauthentication()
            return null
        }

        logOpenSessionError(code)
        return null
    }

    private suspend fun finishOpenSession(
        openedSession: Pair<String, String>,
        masterPassword: String?,
    ): OpenSessionResult {
        val (newSessionCode, encryptedKeychainJson) = openedSession
        preferencesManager.setCSEv1Keychain(encryptedKeychainJson)
        masterPassword?.let {
            val keysJson = CSEv1Keychain.decryptJson(encryptedKeychainJson, it)
            csEv1Keychain.postValue(CSEv1Keychain.fromJson(keysJson))
        }
        sessionCode = newSessionCode
        scheduleKeepAlive(newSessionCode)

        sessionVersion++
        val sessionIdentity = SessionIdentity(
            sessionCode = newSessionCode,
            sessionVersion = sessionVersion,
        )
        _sessionOpen.emit(true)
        return OpenSessionResult(
            opened = true,
            sessionIdentity = sessionIdentity,
        )
    }

    private fun scheduleKeepAlive(newSessionCode: String) {
        serverSettings.value?.let { settings ->
            val keepAliveDelay = (settings.sessionLifetime * 3 / 4 * 1000).toLong()
            workManager.cancelAllWorkByTag(KeepAliveWorker.TAG)
            workManager.enqueue(KeepAliveWorker.getRequest(keepAliveDelay, newSessionCode))
        }
    }

    private fun logRequestSessionError(code: Int) {
        when (code) {
            Error.API_TIMEOUT -> Log.e(
                "API Controller",
                "Timeout requesting session, user ${server.username}"
            )

            Error.API_BAD_RESPONSE -> Log.e(
                "API Controller",
                "Bad response on session request, user ${server.username}"
            )
        }
    }

    private fun logOpenSessionError(code: Int) {
        when (code) {
            Error.API_TIMEOUT -> Log.e(
                "API Controller",
                "Timeout opening session, user ${server.username}"
            )

            Error.API_BAD_RESPONSE -> Log.e(
                "API Controller",
                "Bad response on session open, user ${server.username}"
            )
        }
    }

    /**
     * Closes the current session.
     *
     * @param clearStoredKeychain Whether to delete the saved keychain from app storage.
     * @return A boolean indicating if the session was successfully closed.
     */
    suspend fun closeSession(clearStoredKeychain: Boolean = true): Boolean {
        return if (sessionCode == null || sessionCode?.let { code -> sessionApi.closeSession(code) } == true) {
            clearSession()
            if (clearStoredKeychain) {
                preferencesManager.setCSEv1Keychain(null)
            }
            true
        } else {
            // Session was not closed, some error happened
            false
        }
    }

    private suspend fun closeSessionIfCurrent(
        sessionIdentity: SessionIdentity,
        clearStoredKeychain: Boolean = true
    ): Boolean {
        val currentSessionIdentity = currentSessionIdentity() ?: return true
        if (currentSessionIdentity != sessionIdentity) return true
        return closeSession(clearStoredKeychain)
    }

    fun clearSession() {
        clearSessionState()
    }

    private fun clearSessionState() {
        sessionCode = null
        sessionVersion++
        workManager.cancelAllWorkByTag(KeepAliveWorker.TAG)
        _sessionOpen.value = false
    }

    /**
     * Gets a list of the user passwords via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @return A result with the list of passwords if success, or an error code otherwise.
     */
    suspend fun listPasswords(): Result<List<Password>> {
        if (!sessionOpen.value) return Result.Error(Error.API_NO_SESSION)
        val result = passwordsApi.list(sessionCode)
        if (result.consumeSsoReauthError()) return Result.Error(Error.API_NO_SESSION)
        return result
    }

    /**
     * Gets a list of the user folders via the [FoldersApi] class. This can only be called when a session
     * is open, otherwise an error is thrown.
     *
     * @return A result with the list of folders if success, or an error code otherwise.
     */
    suspend fun listFolders(): Result<List<Folder>> {
        if (!sessionOpen.value) return Result.Error(Error.API_NO_SESSION)
        val result = foldersApi.list(sessionCode)
        if (result.consumeSsoReauthError()) return Result.Error(Error.API_NO_SESSION)
        return result
    }

    /**
     * Creates a new password via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param newPassword [NewPassword] object to be created.
     * @return A boolean stating whether the password was successfully created.
     */
    suspend fun createPassword(newPassword: NewPassword): Boolean {
        if (!sessionOpen.value) return false
        val result = passwordsApi.create(newPassword, sessionCode)
        if (result.consumeSsoReauthError()) return false
        return result is Result.Success
    }

    fun isEndToEndEncryptionKeyAvailable(): Boolean {
        val currentKeychain = csEv1Keychain.value ?: return false
        return currentKeychain.current.isNotBlank()
    }

    suspend fun createBackupKeys(
        userName: String,
        label: String,
        url: String,
        password: String
    ): Boolean {
        if (!sessionOpen.value) return false

        val currentKeychain = csEv1Keychain.value
        val currentServerSettings = serverSettings.value
        if (currentKeychain == null ||
            currentServerSettings == null ||
            currentServerSettings.encryptionCse == 0
        ) return false

        val newPassword = NewPassword(
            password = password.encryptValue(
                currentKeychain.current,
                currentKeychain
            ),
            label = label.encryptValue(
                currentKeychain.current,
                currentKeychain
            ),
            username = userName.encryptValue(
                currentKeychain.current,
                currentKeychain
            ),
            url = url.encryptValue(
                currentKeychain.current,
                currentKeychain
            ),
            notes = BackupAppPassword.WARNING.encryptValue(
                currentKeychain.current,
                currentKeychain
            ),
            customFields = BackupAppPassword.customFieldsJson.encryptValue(
                currentKeychain.current,
                currentKeychain
            ),
            hash = password.sha1Hash()
                .take(currentServerSettings.passwordSecurityHash),
            cseType = CSE_TYPE,
            cseKey = currentKeychain.current,
            folder = "",
            edited = 0,
            hidden = false,
            favorite = false
        )

        val result = passwordsApi.create(newPassword, sessionCode)

        return if (result.consumeSsoReauthError()) false else result is Result.Success
    }

    /**
     * Updates an existing password via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param updatedPassword [UpdatedPassword] object to be updated.
     * @return A boolean stating whether the password was successfully updated.
     */
    suspend fun updatePassword(updatedPassword: UpdatedPassword): Boolean {
        if (!sessionOpen.value) return false
        val result = passwordsApi.update(updatedPassword, sessionCode)
        if (result.consumeSsoReauthError()) return false
        return result is Result.Success
    }

    /**
     * Deletes an existing password via the [PasswordsApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param deletedPassword [DeletedPassword] object to be deleted.
     * @return A boolean stating whether the password was successfully deleted.
     */
    suspend fun deletePassword(deletedPassword: DeletedPassword): Boolean {
        if (!sessionOpen.value) return false
        val result = passwordsApi.delete(deletedPassword, sessionCode)
        if (result.consumeSsoReauthError()) return false
        return result is Result.Success
    }

    /**
     * Generates a random password using user's settings. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @return A string with the generated password, or null if there was an error.
     */
    suspend fun generatePassword(
        strength: Int,
        includeDigits: Boolean,
        includeSymbols: Boolean
    ): String? {
        if (!sessionOpen.value) return null
        val result = serviceApi.password(strength, includeDigits, includeSymbols, sessionCode)
        if (result.consumeSsoReauthError()) return null
        return if (result is Result.Success) result.data else null
    }

    /**
     * Creates a new folder via the [FoldersApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param newFolder [NewFolder] object to be created.
     * @return A boolean stating whether the folder was successfully created.
     */
    suspend fun createFolder(newFolder: NewFolder): Boolean {
        if (!sessionOpen.value) return false
        val result = foldersApi.create(newFolder, sessionCode)
        if (result.consumeSsoReauthError()) return false
        return result is Result.Success
    }

    /**
     * Updates an existing folder via the [FoldersApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param updatedFolder [UpdatedFolder] object to be updated.
     * @return A boolean stating whether the folder was successfully updated.
     */
    suspend fun updateFolder(updatedFolder: UpdatedFolder): Boolean {
        if (!sessionOpen.value) return false
        val result = foldersApi.update(updatedFolder, sessionCode)
        if (result.consumeSsoReauthError()) return false
        return result is Result.Success
    }

    /**
     * Deletes an existing folder via the [FoldersApi] class. This can only be called when a
     * session is open, otherwise an error is thrown.
     *
     * @param deletedFolder [DeletedFolder] object to be deleted.
     * @return A boolean stating whether the folder was successfully deleted.
     */
    suspend fun deleteFolder(deletedFolder: DeletedFolder): Boolean {
        if (!sessionOpen.value) return false
        val result = foldersApi.delete(deletedFolder, sessionCode)
        if (result.consumeSsoReauthError()) return false
        return result is Result.Success
    }

    fun getFaviconServiceRequest(url: String): Pair<String, Server> =
        Pair(serviceApi.getFaviconUrl(url), server)

    fun getAvatarServiceRequest(): Pair<String, Server> =
        Pair(serviceApi.getAvatarUrl(), server)

    suspend fun getFaviconBytes(domain: String): ByteArray? {
        if (!sessionOpen.value) return null
        return try {
            serviceApi.getFaviconBytes(domain, sessionCode)
        } catch (e: SsoReauthenticationRequiredException) {
            Timber.e(e)
            requireSsoReauthentication()
            null
        }
    }

    suspend fun getAvatarBytes(): ByteArray? {
        if (!sessionOpen.value) return null
        return try {
            serviceApi.getAvatarBytes(sessionCode)
        } catch (e: SsoReauthenticationRequiredException) {
            Timber.e(e)
            requireSsoReauthentication()
            null
        }
    }

    companion object {
        private var instance: ApiController? = null

        private const val CSE_SALT_COUNT = 3

        const val CSE_TYPE = "CSEv1r1"

        /**
         * Get the instance of the [ApiController], and create it if null.
         *
         * @param context Context of the application.
         * @return The instance of the controller.
         */
        fun getInstance(context: Context): ApiController {
            synchronized(this) {
                var tempInstance = instance
                val currentServer = runCatching {
                    UserController.getInstance(context.applicationContext).getServer()
                }.getOrNull()

                if (tempInstance == null ||
                    (currentServer != null && tempInstance.server != currentServer)
                ) {
                    tempInstance = ApiController(context)
                    instance = tempInstance
                }

                return tempInstance
            }
        }
    }

}
