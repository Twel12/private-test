package com.hegocre.nextcloudpasswords.backupApp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import com.hegocre.nextcloudpasswords.api.session.SessionFailure
import com.hegocre.nextcloudpasswords.api.session.SessionResult
import com.hegocre.nextcloudpasswords.api.session.invalidatesStoredMasterPassword
import com.hegocre.nextcloudpasswords.api.session.withTemporarySession
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.services.autofill.NCPPasswordBackend
import com.hegocre.nextcloudpasswords.services.autofill.OwnedEntryLookup
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.Result
import com.hegocre.nextcloudpasswords.utils.SecureMasterPasswordStore
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import foundation.e.autofill.PasswordRequestSource
import foundation.e.autofill.PasswordSaveRequest
import foundation.e.autofill.PasswordSaveResult
import foundation.e.backupappapi.BackupAppApi
import foundation.e.backupappapi.BackupKey
import foundation.e.backupappapi.E2eeKeyWrapper
import foundation.e.backupappapi.IBackupKeyCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.UUID

class BackupAppService : Service() {

    companion object {
        private const val KEY_BYTE_SIZE = 48 // 48 bytes → 64 chars
        private const val TAG = "BackupAppService"

        private const val SETTING_KEY_BACKUP_PASSWORD_ID =
            "client.murena.backup.passwordId"

        private const val BACKUP_KEY_TIMEOUT_MS = 150000L // 150 Seconds
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupKeyMutex = Mutex()

    private val implementation = object : BackupAppApi.Stub() {

        override fun getKeyForBackup(callback: IBackupKeyCallback) {
            serviceScope.launch {
                val backupKey = runCatching {
                    withTimeout(BACKUP_KEY_TIMEOUT_MS) {
                        BackupKey(getBackupKey(this@BackupAppService))
                    }
                }.getOrElse { throwable ->
                    when (throwable) {
                        is TimeoutCancellationException ->
                            Log.w(TAG, "timed out fetching backup key", throwable)
                        is CancellationException -> throw throwable
                        else ->
                            Log.e(TAG, "failed to fetch backup key", throwable)
                    }
                    BackupKey(E2eeKeyWrapper.ApiError(shouldRetry = true))
                }

                runCatching {
                    callback.onKeyReady(backupKey)
                }.onFailure { exception ->
                    Log.w(TAG, "failed to deliver backup key to client", exception)
                }
            }
        }
    }

    private fun errorMurenaAccountUnavailable(): E2eeKeyWrapper {
        Log.d(TAG, "murena.io account is not available")
        return E2eeKeyWrapper.MurenaAccountUnavailable()
    }

    private fun errorE2eeUnavailable(): E2eeKeyWrapper {
        Log.d(TAG, "backup api requires user re-authentication for e2ee")
        return E2eeKeyWrapper.E2eeUnavailable()
    }

    private suspend fun getBackupKey(context: Context): E2eeKeyWrapper {
        SsoAccount.getCurrentSingleSignOnAccount(context) ?: return errorMurenaAccountUnavailable()
        return backupKeyMutex.withLock {
            val apiController = ApiController.getInstance(context)
            if (apiController.sessionOpen.value) {
                return@withLock resolveBackupKey(apiController, context)
            }

            val masterPassword = MasterPasswordMemoryStore.get()?.takeIf { it.isNotBlank() }
                ?: return@withLock errorE2eeUnavailable()

            val result = apiController.withTemporarySession(
                masterPassword = masterPassword,
                clearStoredKeychainOnClose = false,
                onCloseFailed = { Log.w(TAG, "failed to close session opened for backup api") },
            ) {
                resolveBackupKey(apiController, context)
            }

            when (result) {
                is SessionResult.Success -> result.value
                is SessionResult.Failure -> {
                    Log.d(TAG, "could not open a session for backup api: ${result.reason}")
                    if (result.reason.invalidatesStoredMasterPassword) {
                        MasterPasswordMemoryStore.clear()
                        SecureMasterPasswordStore(context).clear()
                    }
                    result.reason.toE2eeKeyWrapper()
                }
            }
        }
    }

    private suspend fun resolveBackupKey(
        apiController: ApiController,
        context: Context,
    ): E2eeKeyWrapper {
        val keychain = apiController.currentKeychain()?.takeIf { it.current.isNotBlank() }
        val backend = NCPApplication.passwordBackend(context) as? NCPPasswordBackend
        return when {
            keychain == null -> {
                Log.d(TAG, "session opened but end-to-end encryption is not set up")
                E2eeKeyWrapper.E2eeUnavailable()
            }
            backend == null -> E2eeKeyWrapper.ApiError(shouldRetry = false)
            else -> resolveWithPin(apiController, keychain, backend)
        }
    }

    private suspend fun resolveWithPin(
        apiController: ApiController,
        keychain: CSEv1Keychain,
        backend: NCPPasswordBackend,
    ): E2eeKeyWrapper {
        val pinned = readPinnedKey(apiController, keychain)
        val blocked = pinned.blockingResult()
        return when {
            pinned is PinnedKey.Readable -> pinned.password.asE2eeKey()
            pinned is PinnedKey.ReadFailed -> findWithoutCreating(backend, pinned.pinnedId)
            blocked != null -> {
                Log.w(TAG, "pinned backup key is $pinned; not looking for or creating another")
                blocked
            }
            else -> findOrCreateBackupKey(apiController, backend)
        }
    }

    private suspend fun findOrCreateBackupKey(
        apiController: ApiController,
        backend: NCPPasswordBackend,
    ): E2eeKeyWrapper = when (val found = findBackupKey(backend)) {
        is OwnedEntryLookup.Found -> {
            pinBackupKey(apiController, found.password.id)
            found.password.asE2eeKey()
        }
        OwnedEntryLookup.Absent -> createBackupKey(apiController, backend)
        is OwnedEntryLookup.Ambiguous -> ambiguousBackupKeys(found.count)
        OwnedEntryLookup.SyncFailed -> E2eeKeyWrapper.ApiError(shouldRetry = true)
        OwnedEntryLookup.Inconclusive -> {
            Log.w(TAG, "some passwords could not be decrypted; not creating a backup key")
            E2eeKeyWrapper.ApiError(shouldRetry = true)
        }
    }

    // The pin couldn't be read, so a key may well exist: serve the one the lookup finds (if it is
    // the pinned one), but never create another.
    private suspend fun findWithoutCreating(
        backend: NCPPasswordBackend,
        pinnedId: String?,
    ): E2eeKeyWrapper {
        val found = (findBackupKey(backend) as? OwnedEntryLookup.Found)?.password
        if (found != null && (pinnedId == null || found.id == pinnedId)) return found.asE2eeKey()
        Log.w(TAG, "could not read the pinned backup key; not creating another")
        return E2eeKeyWrapper.ApiError(shouldRetry = true)
    }

    private suspend fun findBackupKey(backend: NCPPasswordBackend): OwnedEntryLookup =
        backend.findOwnedEntry(BackupAppPassword::isOwned)

    private fun ambiguousBackupKeys(count: Int): E2eeKeyWrapper {
        Log.e(TAG, "found $count backup keys with the reserved marker; refusing to pick one")
        return E2eeKeyWrapper.ApiError(shouldRetry = false)
    }

    private suspend fun readPinnedKey(
        apiController: ApiController,
        keychain: CSEv1Keychain,
    ): PinnedKey = when (val setting = apiController.getUserSetting(SETTING_KEY_BACKUP_PASSWORD_ID)) {
        is Result.Error -> PinnedKey.ReadFailed(pinnedId = null)
        is Result.Success -> setting.data?.takeIf(::isUuid)
            ?.let { uuid -> readPinnedEntry(apiController, uuid, keychain) }
            ?: PinnedKey.NotSet
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private suspend fun readPinnedEntry(
        apiController: ApiController,
        uuid: String,
        keychain: CSEv1Keychain,
    ): PinnedKey = when (val shown = apiController.showPassword(uuid)) {
        is Result.Error ->
            if (shown.code == Error.API_NOT_FOUND) PinnedKey.Deleted else PinnedKey.ReadFailed(uuid)
        is Result.Success -> {
            val decrypted = shown.data.decrypt(keychain)
            when {
                decrypted == null -> PinnedKey.Unreadable
                !BackupAppPassword.isOwned(decrypted) -> PinnedKey.Modified
                else -> PinnedKey.Readable(decrypted)
            }
        }
    }

    private suspend fun pinBackupKey(apiController: ApiController, uuid: String) {
        val result = apiController.setUserSetting(SETTING_KEY_BACKUP_PASSWORD_ID, uuid)
        if (result is Result.Error) {
            Log.w(TAG, "could not pin backup key uuid in server settings: ${result.code}")
        }
    }

    private suspend fun createBackupKey(
        apiController: ApiController,
        backend: NCPPasswordBackend,
    ): E2eeKeyWrapper {
        val request = PasswordSaveRequest(
            source = PasswordRequestSource.EXTERNAL_APP,
            packageName = BackupAppPassword.PACKAGE_NAME,
            webDomain = null,
            origin = null,
            username = BackupAppPassword.USERNAME,
            password = generateEncryptionKeys(),
            identityKey = BackupAppPassword.IDENTITY_KEY,
        )
        return when (val saved = backend.save(request, BackupAppPassword.template)) {
            PasswordSaveResult.Saved -> when (val created = findBackupKey(backend)) {
                is OwnedEntryLookup.Found -> {
                    Log.d(TAG, "created new backup key")
                    pinBackupKey(apiController, created.password.id)
                    created.password.asE2eeKey(isNewlyCreated = true)
                }
                is OwnedEntryLookup.Ambiguous -> ambiguousBackupKeys(created.count)
                OwnedEntryLookup.Absent,
                OwnedEntryLookup.SyncFailed,
                OwnedEntryLookup.Inconclusive -> E2eeKeyWrapper.ApiError(shouldRetry = true)
            }

            PasswordSaveResult.NeedsUnlock -> E2eeKeyWrapper.E2eeUnavailable()

            PasswordSaveResult.DuplicateIgnored,
            is PasswordSaveResult.NeedsUserInteraction,
            is PasswordSaveResult.QueuedForRetry,
            is PasswordSaveResult.Failed -> {
                Log.w(TAG, "could not save a new backup key: $saved")
                E2eeKeyWrapper.ApiError(shouldRetry = true)
            }
        }
    }

    private fun generateEncryptionKeys(): String {
        val bytes = ByteArray(KEY_BYTE_SIZE)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun Password.asE2eeKey(isNewlyCreated: Boolean = false): E2eeKeyWrapper {
        val backupKeyId = runCatching { UUID.fromString(id) }
            .getOrElse {
                Log.e(TAG, "backup key id is not a UUID: $id", it)
                return E2eeKeyWrapper.ApiError(shouldRetry = false)
            }

        return E2eeKeyWrapper.E2eeKey(
            id = backupKeyId,
            data = password,
            isNewlyCreated = isNewlyCreated,
            createdTimestamp = created.toLong()
        )
    }

    override fun onBind(intent: Intent): IBinder = implementation

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

}

// AccountUnauthorized and ClientDeauthorized need opposite user actions but share a wire value,
// because the published AIDL contract is frozen.
internal fun SessionFailure.toE2eeKeyWrapper(): E2eeKeyWrapper = when (this) {
    SessionFailure.MasterPasswordMissing,
    SessionFailure.MasterPasswordRejected -> E2eeKeyWrapper.E2eeUnavailable()

    SessionFailure.AccountUnauthorized,
    SessionFailure.ClientDeauthorized,
    SessionFailure.SsoReauthenticationRequired,
    SessionFailure.NoAccount -> E2eeKeyWrapper.MurenaAccountUnavailable()

    SessionFailure.AppPasswordRequired,
    is SessionFailure.Transport -> E2eeKeyWrapper.ApiError(shouldRetry = true)
}

internal sealed interface PinnedKey {
    data object NotSet : PinnedKey
    data object Deleted : PinnedKey
    data class ReadFailed(val pinnedId: String?) : PinnedKey
    data object Unreadable : PinnedKey
    data object Modified : PinnedKey
    data class Readable(val password: Password) : PinnedKey
}

// A pin means a key was handed out before, so anything short of "gone" or "never set" must not
// lead to a new key: backups made with the old one would become unreadable.
internal fun PinnedKey.blockingResult(): E2eeKeyWrapper? = when (this) {
    PinnedKey.NotSet,
    PinnedKey.Deleted,
    is PinnedKey.Readable -> null

    is PinnedKey.ReadFailed -> E2eeKeyWrapper.ApiError(shouldRetry = true)
    PinnedKey.Unreadable -> E2eeKeyWrapper.E2eeUnavailable()
    PinnedKey.Modified -> E2eeKeyWrapper.ApiError(shouldRetry = false)
}
