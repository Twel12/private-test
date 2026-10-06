/*
 *  Copyright MURENA SAS 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.passwords.companion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

interface PasswordsCompanion {
    suspend fun get(id: String): GetResult

    suspend fun save(id: String, secret: String, mode: SaveMode, presentation: EntryPresentation): SaveResult

    suspend fun delete(id: String): DeleteResult
}

class PasswordsCompanionClient(
    context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : PasswordsCompanion {

    private val appContext = context.applicationContext

    override suspend fun get(id: String): GetResult {
        if (!CredentialId.isValid(id)) return Failed(FailureCode.INVALID_ID)
        return when (val call = call { service, callback -> service.get(CompanionCodec.getRequest(id), callback) }) {
            is Call.Delivered -> CompanionCodec.decodeGet(call.bundle)
            is Call.Undelivered -> call.failure
        }
    }

    override suspend fun save(
        id: String,
        secret: String,
        mode: SaveMode,
        presentation: EntryPresentation,
    ): SaveResult {
        if (!CredentialId.isValid(id)) return Failed(FailureCode.INVALID_ID)
        val request = CompanionCodec.saveRequest(SaveRequest(id, secret, mode, presentation))
        return when (val call = call { service, callback -> service.save(request, callback) }) {
            is Call.Delivered -> CompanionCodec.decodeSave(call.bundle)
            is Call.Undelivered -> call.failure
        }
    }

    override suspend fun delete(id: String): DeleteResult {
        if (!CredentialId.isValid(id)) return Failed(FailureCode.INVALID_ID)
        val call = call { service, callback -> service.delete(CompanionCodec.deleteRequest(id), callback) }
        return when (call) {
            is Call.Delivered -> CompanionCodec.decodeDelete(call.bundle)
            is Call.Undelivered -> call.failure
        }
    }

    private sealed interface Call {
        data class Delivered(val bundle: Bundle) : Call
        data class Undelivered(val failure: Failed) : Call
    }

    private suspend fun call(invoke: (ICompanionCredentialService, ICompanionCallback) -> Unit): Call =
        try {
            withTimeout(timeoutMs) { bindAndCall(invoke) }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timed out waiting for Passwords", e)
            Call.Undelivered(Failed(FailureCode.NETWORK))
        }

    private suspend fun bindAndCall(
        invoke: (ICompanionCredentialService, ICompanionCallback) -> Unit,
    ): Call = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val completed = AtomicBoolean(false)
            lateinit var connection: ServiceConnection

            fun complete(call: Call) {
                if (!completed.compareAndSet(false, true)) return
                runCatching { appContext.unbindService(connection) }
                if (cont.isActive) cont.resume(call)
            }

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    val callback = object : ICompanionCallback.Stub() {
                        override fun onResult(result: Bundle?) {
                            complete(
                                result?.let { Call.Delivered(it) } ?: Call.Undelivered(Failed(FailureCode.UNKNOWN)),
                            )
                        }
                    }
                    try {
                        invoke(ICompanionCredentialService.Stub.asInterface(binder), callback)
                    } catch (e: RemoteException) {
                        Log.w(TAG, "Passwords call failed", e)
                        complete(Call.Undelivered(Failed(FailureCode.NETWORK)))
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Passwords refused the call", e)
                        complete(Call.Undelivered(Failed(FailureCode.NOT_ALLOWED)))
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Log.e(TAG, "Passwords call crashed", e)
                        complete(Call.Undelivered(Failed(FailureCode.UNKNOWN)))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) =
                    complete(Call.Undelivered(Failed(FailureCode.NETWORK)))

                override fun onBindingDied(name: ComponentName) =
                    complete(Call.Undelivered(Failed(FailureCode.NETWORK)))

                override fun onNullBinding(name: ComponentName) =
                    complete(Call.Undelivered(Failed(FailureCode.UNKNOWN)))
            }

            cont.invokeOnCancellation { runCatching { appContext.unbindService(connection) } }

            val intent = Intent(CompanionProtocol.SERVICE_ACTION).setPackage(CompanionProtocol.PASSWORDS_PACKAGE)
            val bound = try {
                appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                Log.e(TAG, "Not allowed to bind to Passwords", e)
                complete(Call.Undelivered(Failed(FailureCode.NOT_ALLOWED)))
                return@suspendCancellableCoroutine
            }
            if (!bound) complete(Call.Undelivered(Failed(FailureCode.UNKNOWN)))
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val TAG = "PasswordsCompanion"
    }
}
