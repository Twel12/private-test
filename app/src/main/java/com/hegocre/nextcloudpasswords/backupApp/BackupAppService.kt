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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.UUID

class BackupAppService : Service() {

    companion object {
        private const val KEY_BYTE_SIZE = 48 // 48 bytes → 64 chars
        private const val TAG = "BackupAppService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val openSessionMutex = Mutex()
    private val backupKeyMutex = Mutex()

    private val implementation = object : BackupAppApi.Stub() {

        override fun getKeyForBackup(callback: IBackupKeyCallback) {
            serviceScope.launch {
                val backupKey = runCatching {
                    BackupKey(getBackupKey(this@BackupAppService))
                }.getOrElse { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "failed to fetch backup key", throwable)
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

    private suspend fun openSessionOrGetError(): E2eeKeyWrapper? {
        return openSessionMutex.withLock {
            val apiController = ApiController.getInstance(this)

            if (!apiController.sessionOpen.value) {
                val masterPassword = MasterPasswordMemoryStore.get()?.takeIf { it.isNotBlank() }
                    ?: return@withLock errorE2eeUnavailable()
                return@withLock try {
                    val sessionOpened = apiController.openSession(masterPassword)
                    if (!sessionOpened) {
                        Log.d(TAG, "unable to open a session for backup api")
                        E2eeKeyWrapper.ApiError(shouldRetry = true)
                    } else null
                } catch (_: PWDv1ChallengeMasterKeyNeededException) {
                    errorE2eeUnavailable()
                } catch (_: PWDv1ChallengeMasterKeyInvalidException) {
                    clearMasterPasswordState()
                    errorE2eeUnavailable()
                } catch (_: PWDv1ChallengePasswordException) {
                    clearMasterPasswordState()
                    errorE2eeUnavailable()
                } catch (_: ClientDeauthorizedException) {
                    errorMurenaAccountUnavailable()
                }
            }
            null
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
            val sessionError = openSessionOrGetError()
            val keychain = apiController.csEv1Keychain.value

            when {
                sessionError != null -> sessionError

                keychain?.current.isNullOrBlank() -> {
                    Log.d(TAG, "unable to open a session for backup api")
                    E2eeKeyWrapper.ApiError(shouldRetry = true)
                }

                else -> when (val existingKey = apiController.getBackupKey(chain = keychain)) {
                    is Result.Error -> {
                        Log.d(TAG, "api response failed! ${existingKey.code}")
                        E2eeKeyWrapper.ApiError(shouldRetry = existingKey.code != Error.UNKNOWN)
                    }

                    is Result.Success -> if (existingKey.data != null) {
                        Log.d(TAG, "using previously generated e2ee")
                        existingKey.data.asE2eeKey()
                    } else {
                        createBackupKey(apiController = apiController, keychain = keychain)
                    }
                }
            }
        }
    }

    private fun generateEncryptionKeys(): String {
        val bytes = ByteArray(KEY_BYTE_SIZE)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private suspend fun ApiController.getBackupKey(
        chain: CSEv1Keychain?
    ): Result<Password?> {
        val decrypted = when (val passwords = listPasswords()) {
            is Result.Error -> return passwords
            is Result.Success -> passwords.data.decryptPasswords(chain)
        }
        val matchingKeys = decrypted.filter(BackupAppPassword::matches)
        return if (matchingKeys.size > 1) {
            Log.e(TAG, "multiple backup keys found with the reserved marker")
            Result.Error(Error.UNKNOWN)
        } else {
            Result.Success(matchingKeys.firstOrNull())
        }
    }

    private suspend fun createBackupKey(
        apiController: ApiController,
        keychain: CSEv1Keychain?
    ): E2eeKeyWrapper {
        val newKeysCreated = apiController.createBackupKeys(
            userName = BackupAppPassword.USERNAME,
            label = BackupAppPassword.LABEL,
            url = BackupAppPassword.URI,
            password = generateEncryptionKeys()
        )
        if (!newKeysCreated) {
            Log.d(TAG, "failed to generated backup keys")
            return E2eeKeyWrapper.ApiError(shouldRetry = true)
        }

        val newlyCreatedKey = apiController.getBackupKey(chain = keychain)
        return when {
            newlyCreatedKey is Result.Error ->
                E2eeKeyWrapper.ApiError(shouldRetry = newlyCreatedKey.code != Error.UNKNOWN)
            newlyCreatedKey is Result.Success && newlyCreatedKey.data != null -> {
                Log.d(TAG, "key configured successfully")
                newlyCreatedKey.data.asE2eeKey(isNewlyCreated = true)
            }

            else -> {
                Log.d(TAG, "failed to configure the backup keys")
                E2eeKeyWrapper.ApiError(shouldRetry = true)
            }
        }
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
