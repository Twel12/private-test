package com.hegocre.nextcloudpasswords.ui.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.URLUtil
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import com.hegocre.nextcloudpasswords.api.exceptions.ClientDeauthorizedException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.api.exceptions.SsoReauthenticationRequiredException
import com.hegocre.nextcloudpasswords.data.folder.DeletedFolder
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.folder.FolderController
import com.hegocre.nextcloudpasswords.data.folder.NewFolder
import com.hegocre.nextcloudpasswords.data.folder.UpdatedFolder
import com.hegocre.nextcloudpasswords.data.password.DeletedPassword
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.PasswordController
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.data.serversettings.ServerSettings
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.data.user.UserException
import com.hegocre.nextcloudpasswords.utils.AppLockHelper
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import com.hegocre.nextcloudpasswords.utils.SecureMasterPasswordStore
import com.hegocre.nextcloudpasswords.utils.SsoOkHttpRequest
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.MalformedURLException
import java.net.URL

class PasswordsViewModel(application: Application) : AndroidViewModel(application) {
    private val secureMasterPasswordStore = SecureMasterPasswordStore(application)

    private var masterPassword: MutableLiveData<String?> = MutableLiveData<String?>(null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()


    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean>
        get() = _isUpdating.asStateFlow()

    private val _needsMasterPassword = MutableStateFlow(false)
    val needsMasterPassword: StateFlow<Boolean>
        get() = _needsMasterPassword.asStateFlow()

    private val _masterPasswordInvalid = MutableStateFlow(false)
    val masterPasswordInvalid: StateFlow<Boolean>
        get() = _masterPasswordInvalid.asStateFlow()

    private val _clientDeauthorized = MutableLiveData(false)
    val clientDeauthorized: LiveData<Boolean>
        get() = _clientDeauthorized

    private val apiController = ApiController.getInstance(application)

    val ssoReauthRequired: StateFlow<Boolean>
        get() = apiController.ssoReauthenticationRequired

    fun clearSsoReauthenticationRequired() {
        apiController.clearSsoReauthenticationRequired()
    }

    val sessionOpen
        get() = apiController.sessionOpen

    private val _showSessionOpenError = MutableStateFlow(false)
    val showSessionOpenError: StateFlow<Boolean>
        get() = _showSessionOpenError.asStateFlow()

    private val _pendingSecureMasterPasswordSave = MutableStateFlow(false)
    val pendingSecureMasterPasswordSave: StateFlow<Boolean>
        get() = _pendingSecureMasterPasswordSave.asStateFlow()

    val csEv1Keychain: LiveData<CSEv1Keychain?>
        get() = apiController.csEv1Keychain

    val serverSettings: LiveData<ServerSettings>
        get() = apiController.serverSettings

    private val _awaitingMigration = MutableStateFlow(false)

    /**
     * Whether the migration dialog should currently be shown. Derived from:
     *   - the account being Murena/SSO (local-credential accounts manage E2EE
     *     setup through the regular Nextcloud Passwords flow),
     *   - the server reporting E2EE as disabled, and
     *   - the user not being mid-flight through the Custom Tab (which would
     *     otherwise cause the dialog to flash back over the post-return
     *     re-open).
     */
    val showE2eeMigrationDialog: StateFlow<Boolean> = combine(
        apiController.endToEndEncryptionEnabled,
        _awaitingMigration
    ) { enabled, awaiting ->
        !supportsLocalLogout && enabled == false && !awaiting
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun prepareE2eeMigrationUri(): Uri? {
        if (supportsLocalLogout || _awaitingMigration.value) return null
        val baseUrl = server?.url
        val expectedHost = baseUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        val candidate = baseUrl?.trimEnd('/')?.plus(PASSWORDS_WEB_PATH)
        val uri = candidate?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return when {
            baseUrl == null || expectedHost == null -> {
                Timber.e("No server URL available; cannot start E2EE migration")
                null
            }
            uri == null -> {
                Timber.e("Could not parse migration URL: %s", candidate)
                null
            }
            !URLUtil.isHttpsUrl(uri.toString()) -> {
                Timber.e("Refusing to launch E2EE migration over insecure URL: %s", uri)
                null
            }
            !uri.host.equals(expectedHost, ignoreCase = true) -> {
                Timber.e(
                    "Refusing to launch E2EE migration: host mismatch (expected=%s actual=%s)",
                    expectedHost,
                    uri.host
                )
                null
            }
            else -> uri
        }
    }

    fun onE2eeMigrationLaunched() {
        _awaitingMigration.value = true
    }

    fun onE2eeMigrationLaunchFailed() {
        _awaitingMigration.value = false
    }

    /**
     * Called when the user returns from the Custom Tab. If we were waiting on
     * a migration, clear any stale master password and re-open the session.
     * If E2EE is now enabled, [openSession] naturally throws
     * [PWDv1ChallengeMasterKeyNeededException], which the existing catch
     * block surfaces to the user as the master password prompt — no
     * separate "migration completed" handler needed.
     */
    fun onAppResumedAfterMigration() {
        if (!_awaitingMigration.value) return
        Timber.i("Returned from Murena Passwords web; re-opening session")
        masterPassword.value = null
        clearMasterPasswordState()
        apiController.clearSession()
        viewModelScope.launch {
            // Keep _awaitingMigration true until openSession finishes — otherwise
            // the dialog would briefly flash back over the still-stale
            // endToEndEncryptionEnabled=false before the recheck updates it.
            try {
                openSession(null)
            } finally {
                _awaitingMigration.value = false
            }
        }
    }

    val server
        get() = try {
            UserController.getInstance(getApplication()).getServer()
        } catch (_: UserException) {
            null
        }

    val supportsLocalLogout: Boolean
        get() = OkHttpRequestInterface.getInstance() !is SsoOkHttpRequest

    val passwords: LiveData<List<Password>>
        get() = PasswordController.getInstance(getApplication()).getPasswords()
    val folders: LiveData<List<Folder>>
        get() = FolderController.getInstance(getApplication()).getFolders()

    var visiblePassword = mutableStateOf<Pair<Password, List<String>>?>(null)
        private set
    var visibleFolder = mutableStateOf<Folder?>(null)
        private set

    private val faviconBytesCache = mutableMapOf<String, ByteArray>()
    private var avatarBytesCache: ByteArray? = null

    init {
        val screenLockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context != null && intent != null) {
                    val action = intent.action
                    if (screenLockFilter.matchAction(action)) {
                        AppLockHelper.getInstance(context).enableLock()
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            application.registerReceiver(
                screenOffReceiver,
                screenLockFilter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            application.registerReceiver(screenOffReceiver, screenLockFilter)
        }

        if (!sessionOpen.value) {
            viewModelScope.launch { openSession(password = MasterPasswordMemoryStore.get()) }
        }
    }

    private suspend fun openSession(
        password: String?,
        saveSecurelyAfterUnlock: Boolean = false
    ) {
        _isRefreshing.emit(true)
        _showSessionOpenError.emit(false)
        try {
            if (apiController.openSession(password)) {
                MasterPasswordMemoryStore.set(password)
                masterPassword.postValue(password)
                _needsMasterPassword.emit(false)
                _masterPasswordInvalid.emit(false)
                _showSessionOpenError.emit(false)
                if (saveSecurelyAfterUnlock) {
                    _pendingSecureMasterPasswordSave.emit(true)
                }
                syncPasswordsAndFolders()
                return
            }
            _showSessionOpenError.emit(true)
        } catch (_: PWDv1ChallengeMasterKeyNeededException) {
            _needsMasterPassword.emit(true)
        } catch (_: ClientDeauthorizedException) {
            _clientDeauthorized.postValue(true)
        } catch (_: SsoReauthenticationRequiredException) {
            apiController.requireSsoReauthentication()
        } catch (ex: Exception) {
            when (ex) {
                is PWDv1ChallengeMasterKeyInvalidException, is PWDv1ChallengePasswordException -> {
                    _needsMasterPassword.emit(true)
                    _masterPasswordInvalid.emit(true)
                    masterPassword.postValue(null)
                    clearMasterPasswordState()
                }
                else -> {
                    _showSessionOpenError.emit(true)
                    Timber.e(ex, "Unexpected error opening session")
                }
            }
        } finally {
            _isRefreshing.emit(false)
        }
    }

    fun setMasterPassword(password: String, save: Boolean = false) {
        viewModelScope.launch {
            openSession(password, saveSecurelyAfterUnlock = save)
        }
    }

    fun currentMasterPassword(): String? = MasterPasswordMemoryStore.get()

    fun onSecureMasterPasswordSaveHandled() {
        viewModelScope.launch {
            _pendingSecureMasterPasswordSave.emit(false)
        }
    }

    fun clearMasterPasswordInvalid() {
        viewModelScope.launch {
            _masterPasswordInvalid.emit(false)
        }
    }

    fun requestMasterPassword() {
        viewModelScope.launch {
            _masterPasswordInvalid.emit(false)
            _needsMasterPassword.emit(true)
        }
    }

    fun sync() {
        if (_isRefreshing.value) return

        if (sessionOpen.value) {
            viewModelScope.launch {
                syncPasswordsAndFolders()
            }
        } else {
            viewModelScope.launch {
                openSession(masterPassword.value)
            }
        }
    }

    private suspend fun syncPasswordsAndFolders() {
        _isRefreshing.emit(true)
        try {
            PasswordController.getInstance(getApplication()).syncPasswords()
            FolderController.getInstance(getApplication()).syncFolders()
        } catch (_: SsoReauthenticationRequiredException) {
            apiController.requireSsoReauthentication()
        } finally {
            _isRefreshing.emit(false)
        }
    }

    private fun clearMasterPasswordState() {
        MasterPasswordMemoryStore.clear()
        secureMasterPasswordStore.clear()
    }

    fun setVisiblePassword(password: Password, folderPath: List<String>) {
        visiblePassword.value = Pair(password, folderPath)
    }

    fun setVisibleFolder(folder: Folder?) {
        visibleFolder.value = folder
    }

    fun createPassword(newPassword: NewPassword): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.createPassword(newPassword)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun updatePassword(updatedPassword: UpdatedPassword): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.updatePassword(updatedPassword)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun deletePassword(deletedPassword: DeletedPassword): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.deletePassword(deletedPassword)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun generatePassword(
        strength: Int, includeDigits: Boolean, includeSymbols: Boolean
    ): Deferred<String?> {
        return viewModelScope.async {
            return@async apiController.generatePassword(strength, includeDigits, includeSymbols)
        }
    }

    fun createFolder(newFolder: NewFolder): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.createFolder(newFolder)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun updateFolder(updatedFolder: UpdatedFolder): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.updateFolder(updatedFolder)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    fun deleteFolder(deletedFolder: DeletedFolder): Deferred<Boolean> {
        return viewModelScope.async {
            _isUpdating.value = true
            if (!apiController.deleteFolder(deletedFolder)) {
                _isUpdating.value = false
                return@async false
            }
            sync()
            _isUpdating.value = false
            true
        }
    }

    @Composable
    fun getPainterForUrl(url: String): Painter {
        val context = LocalContext.current
        val isSessionOpen = sessionOpen.collectAsState(initial = false).value
        val domain = try {
            URL(url).host
        } catch (_: MalformedURLException) {
            url
        }
        val faviconBytes = produceState(
            initialValue = faviconBytesCache[domain],
            key1 = domain,
            key2 = isSessionOpen
        ) {
            if (value == null && isSessionOpen) {
                val fetched = apiController.getFaviconBytes(domain)
                if (fetched != null) {
                    faviconBytesCache[domain] = fetched
                }
                value = fetched
            }
        }.value

        return rememberAsyncImagePainter(
            ImageRequest.Builder(context).apply {
                data(faviconBytes)
                if (faviconBytes != null) {
                    memoryCacheKey("favicon:$domain")
                    diskCacheKey("favicon:$domain")
                }
                crossfade(false)
                val lockDrawable = context.getDrawable(R.drawable.ic_lock)?.apply {
                    setTintList(
                        ColorStateList.valueOf(
                            MaterialTheme.colorScheme.primary.toArgb()
                        )
                    )
                }
                placeholder(lockDrawable)
                fallback(lockDrawable)
                error(lockDrawable)
            }.build()
        )
    }

    @Composable
    fun getPainterForAvatar(): Painter {
        val context = LocalContext.current
        val isSessionOpen = sessionOpen.collectAsState(initial = false).value

        val avatarBytes = produceState<ByteArray?>(
            initialValue = avatarBytesCache,
            key1 = "avatar",
            key2 = isSessionOpen
        ) {
            if (value == null && isSessionOpen) {
                val fetched = apiController.getAvatarBytes()
                if (fetched != null) {
                    avatarBytesCache = fetched
                }
                value = fetched
            }
        }.value

        return rememberAsyncImagePainter(
            model = ImageRequest.Builder(context).apply {
                data(avatarBytes)
                if (avatarBytes != null) {
                    memoryCacheKey("avatar:${server?.username}")
                    diskCacheKey("avatar:${server?.username}")
                }
                crossfade(false)
                val accountDrawable = context.getDrawable(R.drawable.ic_account_circle)?.apply {
                    setTintList(
                        ColorStateList.valueOf(
                            MaterialTheme.colorScheme.primary.toArgb()
                        )
                    )
                }
                placeholder(accountDrawable)
                fallback(accountDrawable)
                error(accountDrawable)
            }.build()
        )
    }

    companion object {
        private const val PASSWORDS_WEB_PATH = "/index.php/apps/passwords/"
    }
}
