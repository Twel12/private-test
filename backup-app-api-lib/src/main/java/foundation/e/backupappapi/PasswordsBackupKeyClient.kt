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
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PasswordsBackupKeyClient {

    private val TAG = PasswordsBackupKeyClient::class.java.simpleName

    suspend fun getBackupKey(context: Context): BackupKey? {
        val component = PasswordsApp.backupApiServiceComponent()
        return runCatching {
            withTimeout(5_000) {
                bindAndFetchKey(context, component)
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to get backup key from Passwords service", e)
        }.getOrNull()
    }

    private suspend fun bindAndFetchKey(
        context: Context,
        componentName: ComponentName,
    ): BackupKey? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->

            val intent = Intent().setComponent(componentName)
            intent.action = PasswordsApp.BACKUP_API_ACTION
            lateinit var connection: ServiceConnection

            fun complete(result: Result<BackupKey?>) {
                if (cont.isActive) {
                    result.fold(
                        onSuccess = cont::resume,
                        onFailure = cont::resumeWithException
                    )
                }
                safeUnbind(context, connection)
            }

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val api = BackupAppApi.Stub.asInterface(service)
                    try {
                        api.getKeyForBackup(object : IBackupKeyCallback.Stub() {
                            override fun onKeyReady(key: BackupKey?) {
                                complete(Result.success(key))
                            }
                        })
                    } catch (e: Exception) {
                        Log.e(TAG, "getKeyForBackup() failed", e)
                        complete(Result.failure(e))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }

            cont.invokeOnCancellation {
                safeUnbind(context, connection)
            }

            val bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.e(TAG, "bindService($componentName) failed", e)
                false
            }

            if (!bound) {
                Log.e(TAG, "bindService($componentName) returned false")
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }
    }

    private fun safeUnbind(context: Context, connection: ServiceConnection) {
        runCatching { context.unbindService(connection) }
    }
}
