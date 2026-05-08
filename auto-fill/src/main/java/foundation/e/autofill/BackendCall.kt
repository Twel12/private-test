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
package foundation.e.autofill

import android.os.CancellationSignal
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private val backendCallScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

internal fun <T> runBackendCall(
    cancellationSignal: CancellationSignal? = null,
    call: suspend () -> T,
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit
) = backendCallScope.launchBackendCall(
    cancellationSignal = cancellationSignal,
    call = call,
    shouldDeliver = { true },
    onSuccess = onSuccess,
    onError = onError
)

internal fun <T> LifecycleOwner.runBackendCall(
    call: suspend () -> T,
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit
): Job = lifecycleScope.launchBackendCall(
    cancellationSignal = null,
    call = call,
    shouldDeliver = { lifecycle.currentState != Lifecycle.State.DESTROYED },
    onSuccess = onSuccess,
    onError = onError
)

private fun <T> CoroutineScope.launchBackendCall(
    cancellationSignal: CancellationSignal?,
    call: suspend () -> T,
    shouldDeliver: () -> Boolean,
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit
): Job {
    val job = launch(Dispatchers.Main.immediate) {
        try {
            val result = withContext(Dispatchers.IO) { call() }
            if (shouldDeliver()) {
                deliverBackendSuccess(result, onSuccess, onError)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (shouldDeliver()) {
                deliverBackendError(error, onError)
            }
        }
    }
    cancellationSignal?.setOnCancelListener { job.cancel() }
    return job
}

private fun <T> deliverBackendSuccess(
    result: T,
    onSuccess: (T) -> Unit,
    onError: (Throwable) -> Unit
) {
    try {
        onSuccess(result)
    } catch (error: Throwable) {
        deliverBackendError(error, onError)
    }
}

private fun deliverBackendError(
    error: Throwable,
    onError: (Throwable) -> Unit
) {
    try {
        onError(error)
    } catch (callbackError: Throwable) {
        Timber.e(callbackError, "Backend error callback failed")
    }
}

internal fun MurenaPasswordBackend.reportSafely(event: PasswordEvent) {
    runBackendCall(
        call = { report(event) },
        onSuccess = {},
        onError = {}
    )
}
