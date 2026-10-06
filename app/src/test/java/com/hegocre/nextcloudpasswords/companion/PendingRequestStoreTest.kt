package com.hegocre.nextcloudpasswords.companion

import foundation.e.passwords.companion.EntryPresentation
import foundation.e.passwords.companion.SaveMode
import foundation.e.passwords.companion.SaveRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingRequestStoreTest {

    private var now = 0L
    private val store = PendingRequestStore(now = { now }, ttlMs = 1_000L)
    private val request = PendingRequest(FMD, PendingOperation.Get)

    @Test
    fun `a stored request can be read until it is removed`() {
        val token = store.put(request)
        assertEquals(request, store.peek(token))
        assertEquals(request, store.peek(token))
        store.remove(token)
        assertNull(store.peek(token))
    }

    @Test
    fun `a request expires after its lifetime`() {
        val token = store.put(request)
        now = 1_001L
        assertNull(store.peek(token))
    }

    @Test
    fun `tokens are unique and unknown tokens find nothing`() {
        assertNotEquals(store.put(request), store.put(request))
        assertNull(store.peek("unknown"))
        assertNull(store.peek(null))
    }

    @Test
    fun `a pending save does not print its secret`() {
        val save = PendingOperation.Save(
            SaveRequest("id", "s3cret-value", SaveMode.CREATE_ONLY, EntryPresentation("l", "u", "n"))
        )
        assertFalse(PendingRequest(FMD, save).toString().contains("s3cret-value"))
    }
}
