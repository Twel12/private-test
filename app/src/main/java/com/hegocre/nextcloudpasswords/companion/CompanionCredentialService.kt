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
package com.hegocre.nextcloudpasswords.companion

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import foundation.e.passwords.companion.CompanionCodec
import foundation.e.passwords.companion.CompanionProtocol
import foundation.e.passwords.companion.CompanionResult
import foundation.e.passwords.companion.CredentialId
import foundation.e.passwords.companion.Failed
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.ICompanionCallback
import foundation.e.passwords.companion.ICompanionCredentialService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CompanionCredentialService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vault by lazy { CompanionComponents.vault(this) }

    private val binder = object : ICompanionCredentialService.Stub() {
        override fun getVersion(): Int = CompanionProtocol.VERSION

        override fun get(request: Bundle?, callback: ICompanionCallback?) = dispatch(callback) { caller ->
            CompanionCodec.readId(request)?.let { PendingRequest(Owner(caller, it), PendingOperation.Get) }
        }

        override fun save(request: Bundle?, callback: ICompanionCallback?) = dispatch(callback) { caller ->
            CompanionCodec.readSaveRequest(request)
                ?.let { PendingRequest(Owner(caller, it.id), PendingOperation.Save(it)) }
        }

        override fun delete(request: Bundle?, callback: ICompanionCallback?) = dispatch(callback) { caller ->
            CompanionCodec.readId(request)?.let { PendingRequest(Owner(caller, it), PendingOperation.Delete) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun dispatch(callback: ICompanionCallback?, parse: (String) -> PendingRequest?) {
        if (callback == null) return
        // The calling identity is only valid on the binder thread, so check it before switching threads.
        val caller = try {
            CallerResolver.resolve(
                hasPermission = checkCallingPermission(CompanionProtocol.PERMISSION) ==
                    PackageManager.PERMISSION_GRANTED,
                packages = packageManager.getPackagesForUid(Binder.getCallingUid()),
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not resolve the companion caller", e)
            null
        }
        scope.launch {
            val result = try {
                handle(caller, parse)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "A companion call failed", e)
                Failed(FailureCode.UNKNOWN)
            }
            deliver(callback, result)
        }
    }

    private suspend fun handle(caller: String?, parse: (String) -> PendingRequest?): CompanionResult {
        if (caller == null) return Failed(FailureCode.NOT_ALLOWED)
        val request = parse(caller)?.takeIf { CredentialId.isValid(it.owner.credentialId) }
            ?: return Failed(FailureCode.INVALID_ID)
        return execute(request)
    }

    private suspend fun execute(request: PendingRequest): CompanionResult {
        val outcome = withTimeoutOrNull(CALL_TIMEOUT_MS) { CompanionRunner.run(vault, request) }
            ?: return Failed(FailureCode.NETWORK)
        return CompanionRunner.toResult(outcome) {
            CompanionIntents.request(this, PendingRequestStore.shared.put(request))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deliver(callback: ICompanionCallback, result: CompanionResult) {
        try {
            callback.onResult(CompanionCodec.encode(result))
        } catch (e: Exception) {
            Log.w(TAG, "Could not deliver a companion result", e)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CompanionService"
        const val CALL_TIMEOUT_MS = 110_000L
    }
}
