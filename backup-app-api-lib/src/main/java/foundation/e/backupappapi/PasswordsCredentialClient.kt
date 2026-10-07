/*
 * Copyright (C) 2026 MURENA SAS
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
 */
package foundation.e.backupappapi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

object PasswordsCredentialClient {

    const val FLAG_REPLACE = 1
    const val FLAG_READ_ONLY = 2

    private const val TAG = "PasswordsCredentialClient"
    private const val CALL_TIMEOUT_MS = 120_000L

    suspend fun get(context: Context, key: String): CredentialResult =
        call(context) { api, callback -> api.get(key, callback) }

    suspend fun save(
        context: Context,
        key: String,
        username: String,
        secret: String,
        flags: Int,
    ): CredentialResult =
        call(context) { api, callback -> api.save(key, username, secret, flags, callback) }

    private suspend fun call(
        context: Context,
        invoke: (BackupAppApi, ICredentialCallback) -> Unit,
    ): CredentialResult =
        withTimeoutOrNull(CALL_TIMEOUT_MS) { bindAndCall(context.applicationContext, invoke) }
            ?: CredentialResult.error(retryable = true).also {
                Log.w(TAG, "Timed out waiting for Passwords")
            }

    private suspend fun bindAndCall(
        context: Context,
        invoke: (BackupAppApi, ICredentialCallback) -> Unit,
    ): CredentialResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val intent = Intent(PasswordsApp.BACKUP_API_ACTION)
                .setComponent(PasswordsApp.backupApiServiceComponent())

            val completed = AtomicBoolean(false)
            val binderRef = AtomicReference<IBinder?>(null)
            val deathRef = AtomicReference<IBinder.DeathRecipient?>(null)
            lateinit var connection: ServiceConnection

            fun cleanup() {
                deathRef.getAndSet(null)?.let { recipient ->
                    binderRef.get()?.let { runCatching { it.unlinkToDeath(recipient, 0) } }
                }
                runCatching { context.unbindService(connection) }
            }

            fun complete(result: CredentialResult) {
                if (!completed.compareAndSet(false, true)) return
                if (cont.isActive) cont.resume(result)
                cleanup()
            }

            fun transientError() = complete(CredentialResult.error(retryable = true))

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    binderRef.set(service)
                    val recipient = IBinder.DeathRecipient {
                        Log.e(TAG, "Passwords died before answering")
                        transientError()
                    }
                    deathRef.set(recipient)
                    try {
                        service.linkToDeath(recipient, 0)
                        invoke(
                            BackupAppApi.Stub.asInterface(service),
                            object : ICredentialCallback.Stub() {
                                override fun onResult(result: CredentialResult?) {
                                    complete(result ?: CredentialResult.error(retryable = true))
                                }
                            }
                        )
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Call to Passwords failed", e)
                        transientError()
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) = transientError()

                override fun onBindingDied(name: ComponentName) = transientError()

                override fun onNullBinding(name: ComponentName) = transientError()
            }

            cont.invokeOnCancellation { cleanup() }

            val bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.e(TAG, "Not allowed to bind to Passwords", e)
                complete(CredentialResult.error(retryable = false))
                return@suspendCancellableCoroutine
            }
            if (!bound) {
                Log.e(TAG, "Passwords is not available")
                transientError()
            }
        }
    }
}
