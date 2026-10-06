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
        if (token == null) return null
        val timed = requests[token] ?: return null
        if (now() - timed.at > ttlMs) {
            requests.remove(token)
            return null
        }
        return timed.request
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
