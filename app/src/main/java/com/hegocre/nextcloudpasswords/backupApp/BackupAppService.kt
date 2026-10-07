package com.hegocre.nextcloudpasswords.backupApp

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.services.autofill.NCPPasswordBackend
import com.hegocre.nextcloudpasswords.ui.activities.MainActivity
import foundation.e.backupappapi.BackupAppApi
import foundation.e.backupappapi.ICredentialCallback
import foundation.e.backupappapi.PasswordsCredentialClient
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

class BackupAppService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val backend: NCPPasswordBackend
        get() = NCPApplication.passwordBackend(this) as NCPPasswordBackend

    private val implementation = object : BackupAppApi.Stub() {

        override fun get(key: String?, callback: ICredentialCallback?) {
            val caller = callingPackage()
            respond(callback) {
                if (caller == null || key.isNullOrBlank()) {
                    OwnedCredentialResult.Failed(retryable = false)
                } else {
                    backend.findOwned(caller, key)
                }
            }
        }

        override fun save(
            key: String?,
            username: String?,
            secret: String?,
            flags: Int,
            callback: ICredentialCallback?
        ) {
            val caller = callingPackage()
            respond(callback) {
                if (caller == null || key.isNullOrBlank() || secret.isNullOrEmpty()) {
                    OwnedCredentialResult.Failed(retryable = false)
                } else {
                    backend.saveOwned(
                        packageName = caller,
                        key = key,
                        username = username.orEmpty(),
                        secret = secret,
                        replace = (flags and PasswordsCredentialClient.FLAG_REPLACE) != 0,
                        readOnly = (flags and PasswordsCredentialClient.FLAG_READ_ONLY) != 0
                    )
                }
            }
        }
    }

    private fun callingPackage(): String? =
        packageManager.getPackagesForUid(Binder.getCallingUid())?.singleOrNull()

    private fun respond(
        callback: ICredentialCallback?,
        block: suspend () -> OwnedCredentialResult
    ) {
        if (callback == null) return
        serviceScope.launch {
            val result = try {
                withTimeout(CALL_TIMEOUT_MS) { mutex.withLock { block() } }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "credential call timed out", e)
                OwnedCredentialResult.Failed(retryable = true)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "credential call failed", e)
                OwnedCredentialResult.Failed(retryable = true)
            }
            try {
                callback.onResult(result.toCredentialResult(::unlockIntent))
            } catch (e: RemoteException) {
                Log.w(TAG, "caller went away before the result was delivered", e)
            }
        }
    }

    private fun unlockIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_UNLOCK_FOR_CALLER, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    override fun onBind(intent: Intent): IBinder = implementation

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "BackupAppService"
        const val CALL_TIMEOUT_MS = 100_000L
    }
}
