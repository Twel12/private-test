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

import foundation.e.passwords.companion.SaveRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface PendingOperation {
    data object Get : PendingOperation
    data class Save(val request: SaveRequest) : PendingOperation
    data object Delete : PendingOperation
}

data class PendingRequest(val owner: Owner, val operation: PendingOperation)

/** Operations waiting for the user, kept in memory only so secrets never travel in intents. */
class PendingRequestStore(
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = TTL_MS,
) {
    private val requests = ConcurrentHashMap<String, Timed>()

    fun put(request: PendingRequest): String {
        prune()
        val token = UUID.randomUUID().toString()
        requests[token] = Timed(request, now())
        return token
    }

    fun peek(token: String?): PendingRequest? {
        // Pruning on every read drops expired entries, so a pending save's secret never lingers.
        prune()
        return token?.let { requests[it]?.request }
    }

    fun remove(token: String?) {
        if (token != null) requests.remove(token)
    }

    private fun prune() {
        val oldest = now() - ttlMs
        requests.entries.removeIf { it.value.at < oldest }
    }

    private data class Timed(val request: PendingRequest, val at: Long)

    companion object {
        const val TTL_MS = 10 * 60 * 1000L
        val shared = PendingRequestStore()
    }
}
