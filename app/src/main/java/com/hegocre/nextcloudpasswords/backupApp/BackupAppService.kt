package com.hegocre.nextcloudpasswords.backupApp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import com.hegocre.nextcloudpasswords.api.exceptions.ClientDeauthorizedException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyNeededException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.PasswordController
import com.hegocre.nextcloudpasswords.databases.AppDatabase
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.Result
import com.hegocre.nextcloudpasswords.utils.SecureMasterPasswordStore
import com.hegocre.nextcloudpasswords.utils.SsoAccount
import com.hegocre.nextcloudpasswords.utils.decryptPasswords
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
    private val openSessionMutex = Mutex()
    private val backupKeyMutex = Mutex()

    @Volatile
    private var isUuidPinned = false

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

    private data class BackupSessionState(
        val error: E2eeKeyWrapper? = null,
        val sessionLease: ApiController.SessionLease? = null,
    )

    private suspend fun openSessionOrGetError(): BackupSessionState {
        return openSessionMutex.withLock {
            val apiController = ApiController.getInstance(this)

            if (!apiController.sessionOpen.value) {
                val masterPassword = MasterPasswordMemoryStore.get()?.takeIf { it.isNotBlank() }
                    ?: return@withLock BackupSessionState(error = errorE2eeUnavailable())
                return@withLock try {
                    when (val temporarySession = apiController.openTemporarySession(
                        masterPassword = masterPassword,
                        clearStoredKeychainOnClose = false,
                    )) {
                        is ApiController.TemporarySessionResult.Opened ->
                            BackupSessionState(sessionLease = temporarySession.lease)

                        ApiController.TemporarySessionResult.AlreadyOpen ->
                            BackupSessionState()

                        ApiController.TemporarySessionResult.Failed -> {
                            Log.d(TAG, "unable to open a session for backup api")
                            BackupSessionState(error = E2eeKeyWrapper.ApiError(shouldRetry = true))
                        }
                    }
                } catch (_: PWDv1ChallengeMasterKeyNeededException) {
                    BackupSessionState(error = errorE2eeUnavailable())
                } catch (_: PWDv1ChallengeMasterKeyInvalidException) {
                    clearMasterPasswordState()
                    BackupSessionState(error = errorE2eeUnavailable())
                } catch (_: PWDv1ChallengePasswordException) {
                    clearMasterPasswordState()
                    BackupSessionState(error = errorE2eeUnavailable())
                } catch (_: ClientDeauthorizedException) {
                    BackupSessionState(error = errorMurenaAccountUnavailable())
                }
            }
            BackupSessionState()
        }
    }

    private fun clearMasterPasswordState() {
        MasterPasswordMemoryStore.clear()
        SecureMasterPasswordStore(this).clear()
    }

    private suspend fun getBackupKey(context: Context): E2eeKeyWrapper {
        SsoAccount.getCurrentSingleSignOnAccount(context) ?: return errorMurenaAccountUnavailable()
        return backupKeyMutex.withLock {
            val apiController = ApiController.getInstance(context)
            val sessionState = openSessionOrGetError()
            sessionState.error?.let { return@withLock it }

            val resolve: suspend () -> E2eeKeyWrapper = {
                resolveBackupKey(apiController, context)
            }

            sessionState.sessionLease?.use(
                onCloseFailed = {
                    Log.w(TAG, "failed to close session opened for backup api")
                },
                block = resolve,
            ) ?: resolve()
        }
    }

    private suspend fun resolveBackupKey(
        apiController: ApiController,
        context: Context,
    ): E2eeKeyWrapper {
        val keychain = apiController.currentKeychain()?.takeIf { it.current.isNotBlank() }
            ?: run {
                Log.d(TAG, "session opened but no keychain available; treat as retryable")
                return E2eeKeyWrapper.ApiError(shouldRetry = true)
            }

        return fetchByStoredUuid(apiController, keychain)
            ?.also { if (it is E2eeKeyWrapper.E2eeKey) isUuidPinned = true }
            ?: servedFromCache(apiController, context, keychain)
            ?: syncThenServeOrCreate(apiController, context, keychain)
    }

    private suspend fun servedFromCache(
        apiController: ApiController,
        context: Context,
        keychain: CSEv1Keychain,
    ): E2eeKeyWrapper? =
        pickAndPinSingleKey(
            apiController,
            matches = findInLocalCache(context, keychain),
            isNewlyCreated = false,
            matchLogMsg = "served backup key from local cache",
        )

    private suspend fun pickAndPinSingleKey(
        apiController: ApiController,
        matches: List<Password>,
        isNewlyCreated: Boolean,
        matchLogMsg: String,
    ): E2eeKeyWrapper? = when (matches.size) {
        0 -> null
        1 -> matches.single().let { hit ->
            Log.d(TAG, matchLogMsg)
            ensureKeyUuidPinned(apiController, hit.id)
            hit.asE2eeKey(isNewlyCreated = isNewlyCreated)
        }
        else -> {
            Log.e(TAG, "found ${matches.size} backup keys with the reserved marker; refusing to pin")
            E2eeKeyWrapper.ApiError(shouldRetry = false)
        }
    }

    private suspend fun syncThenServeOrCreate(
        apiController: ApiController,
        context: Context,
        keychain: CSEv1Keychain,
    ): E2eeKeyWrapper {
        Log.d(TAG, "local cache miss; syncing passwords from server")
        val synced = runCatching { PasswordController.getInstance(context).syncPasswords() }
            .onFailure { Log.w(TAG, "syncPasswords failed during backup-key lookup", it) }
            .isSuccess
        if (!synced) {
            return E2eeKeyWrapper.ApiError(shouldRetry = true)
        }
        return servedFromCache(apiController, context, keychain)
            ?: createBackupKey(apiController, keychain, context)
    }

    private suspend fun fetchByStoredUuid(
        apiController: ApiController,
        keychain: CSEv1Keychain,
    ): E2eeKeyWrapper? =
        when (val result = apiController.getUserSetting(SETTING_KEY_BACKUP_PASSWORD_ID)) {
            is Result.Error -> {
                Log.w(TAG, "could not read pinned uuid: ${result.code}; falling back to cache")
                null
            }
            is Result.Success -> {
                val storedUuid = result.data?.takeIf { it.isNotBlank() }
                if (storedUuid == null) null
                else resolvePinnedKey(apiController, storedUuid, keychain)
            }
        }

    private suspend fun resolvePinnedKey(
        apiController: ApiController,
        storedUuid: String,
        keychain: CSEv1Keychain,
    ): E2eeKeyWrapper? =
        when (val result = apiController.showPassword(storedUuid)) {
            is Result.Success ->
                result.data.decrypt(keychain)?.takeIf(BackupAppPassword::matches)?.asE2eeKey()
            is Result.Error -> {
                if (result.code != Error.API_NOT_FOUND) {
                    Log.w(TAG, "show by pinned uuid failed: ${result.code}; falling back to cache")
                }
                null
            }
        }

    private suspend fun findInLocalCache(
        context: Context,
        keychain: CSEv1Keychain,
    ): List<Password> =
        AppDatabase.getInstance(context).passwordDao
            .fetchAllPasswordsList()
            .filter { !it.trashed && !it.hidden }
            .decryptPasswords(keychain)
            .filter(BackupAppPassword::matches)

    private suspend fun ensureKeyUuidPinned(apiController: ApiController, uuid: String) {
        if (isUuidPinned) return

        val currentlyPinned = when (val result = apiController.getUserSetting(SETTING_KEY_BACKUP_PASSWORD_ID)) {
            is Result.Success -> result.data?.takeIf { it.isNotBlank() }
            is Result.Error -> {
                Log.w(TAG, "could not read pinned uuid before write: ${result.code}; attempting write")
                null
            }
        }

        if (currentlyPinned == uuid) {
            isUuidPinned = true
            Log.d(TAG, "backup-key uuid already pinned in server settings")
            return
        }

        val writeResult = runCatching {
            apiController.setUserSetting(SETTING_KEY_BACKUP_PASSWORD_ID, uuid)
        }
        writeResult
            .onSuccess {
                if (it is Result.Success) {
                    isUuidPinned = true
                    Log.d(TAG, "pinned backup-key uuid to server settings")
                } else if (it is Result.Error) {
                    Log.w(TAG, "could not persist backup-key UUID to server settings: ${it.code}")
                }
            }
            .onFailure { Log.w(TAG, "could not persist backup-key UUID to server settings", it) }
    }

    private suspend fun createBackupKey(
        apiController: ApiController,
        keychain: CSEv1Keychain,
        context: Context,
    ): E2eeKeyWrapper {
        val newKeysCreated = apiController.createBackupKeys(
            userName = BackupAppPassword.USERNAME,
            label = BackupAppPassword.LABEL,
            url = BackupAppPassword.URI,
            password = generateEncryptionKeys()
        )
        if (!newKeysCreated) {
            Log.d(TAG, "failed to generate backup keys")
            return E2eeKeyWrapper.ApiError(shouldRetry = true)
        }

        runCatching { PasswordController.getInstance(context).syncPasswords() }
            .onFailure { Log.w(TAG, "syncPasswords failed after creating backup key", it) }

        return pickAndPinSingleKey(
            apiController,
            matches = findInLocalCache(context, keychain),
            isNewlyCreated = true,
            matchLogMsg = "created new backup key",
        ) ?: run {
            Log.d(TAG, "newly created backup key not found in cache after sync")
            E2eeKeyWrapper.ApiError(shouldRetry = true)
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
