/*
 * Copyright (c) 2026 e Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.backupappapi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

object PasswordsBackupKeyClient {

    private val TAG = PasswordsBackupKeyClient::class.java.simpleName

    private const val FETCH_TIMEOUT_MS = 180000L // 180 seconds

    private fun transientError(): BackupKey =
        BackupKey(E2eeKeyWrapper.ApiError(shouldRetry = true))

    suspend fun getBackupKey(context: Context): BackupKey? {
        val component = PasswordsApp.backupApiServiceComponent()
        return runCatching {
            withTimeout(FETCH_TIMEOUT_MS) {
                bindAndFetchKey(context, component)
            }
        }.onFailure { e ->
            when (e) {
                is TimeoutCancellationException ->
                    Log.w(TAG, "Timed out waiting for backup key from Passwords service")
                is CancellationException -> throw e
                else -> Log.e(TAG, "Failed to get backup key from Passwords service", e)
            }
        }.getOrDefault(transientError())
    }

    private suspend fun bindAndFetchKey(
        context: Context,
        componentName: ComponentName,
    ): BackupKey = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->

            val intent = Intent().setComponent(componentName)
            intent.action = PasswordsApp.BACKUP_API_ACTION

            val completed = AtomicBoolean(false)
            val binderRef = AtomicReference<IBinder?>(null)
            val deathRef = AtomicReference<IBinder.DeathRecipient?>(null)
            lateinit var connection: ServiceConnection

            fun cleanup() {
                deathRef.getAndSet(null)?.let { recipient ->
                    binderRef.get()?.let { binder ->
                        runCatching { binder.unlinkToDeath(recipient, 0) }
                    }
                }
                safeUnbind(context, connection)
            }

            fun complete(result: BackupKey) {
                if (!completed.compareAndSet(false, true)) return
                if (cont.isActive) cont.resume(result)
                cleanup()
            }

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    binderRef.set(service)

                    val recipient = IBinder.DeathRecipient {
                        Log.e(TAG, "Passwords service died before delivering the backup key")
                        complete(transientError())
                    }
                    deathRef.set(recipient)
                    try {
                        service.linkToDeath(recipient, 0)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Passwords service already dead on connect", e)
                        complete(transientError())
                        return
                    }

                    val api = BackupAppApi.Stub.asInterface(service)
                    try {
                        api.getKeyForBackup(object : IBackupKeyCallback.Stub() {
                            override fun onKeyReady(key: BackupKey?) {
                                complete(key ?: transientError())
                            }
                        })
                    } catch (e: RemoteException) {
                        Log.e(TAG, "getKeyForBackup() RemoteException", e)
                        complete(transientError())
                    } catch (e: Exception) {
                        Log.e(TAG, "getKeyForBackup() failed", e)
                        complete(transientError())
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    Log.w(TAG, "Passwords service disconnected before delivering the backup key")
                    complete(transientError())
                }

                override fun onBindingDied(name: ComponentName) {
                    Log.w(TAG, "Binding to Passwords service died")
                    complete(transientError())
                }

                override fun onNullBinding(name: ComponentName) {
                    Log.e(TAG, "Passwords service returned a null binding")
                    complete(transientError())
                }
            }

            cont.invokeOnCancellation { cleanup() }

            val bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.e(TAG, "bindService($componentName) failed", e)
                false
            }

            if (!bound) {
                Log.e(TAG, "bindService($componentName) returned false")
                complete(transientError())
            }
        }
    }

    private fun safeUnbind(context: Context, connection: ServiceConnection) {
        runCatching { context.unbindService(connection) }
    }
}
