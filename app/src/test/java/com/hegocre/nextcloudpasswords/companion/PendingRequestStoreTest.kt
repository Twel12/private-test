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
    private val pendingSave = PendingRequest(
        FMD,
        PendingOperation.Save(SaveRequest("id", "secret", SaveMode.REPLACE, EntryPresentation("l", "u", "n"))),
    )

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

    // Each test winds the clock back afterwards: an entry that was only skipped, not dropped, would be
    // readable again.
    @Test
    fun `an expired pending save is dropped by a later peek of another token`() {
        val token = store.put(pendingSave)
        now = 1_001L
        assertNull(store.peek("other"))
        now = 0L
        assertNull(store.peek(token))
    }

    @Test
    fun `an expired pending save is dropped by a later put`() {
        val token = store.put(pendingSave)
        now = 1_001L
        store.put(request)
        now = 0L
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
