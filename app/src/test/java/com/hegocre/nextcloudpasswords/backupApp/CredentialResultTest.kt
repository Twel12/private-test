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
package com.hegocre.nextcloudpasswords.backupApp

import foundation.e.backupappapi.CredentialResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CredentialResultTest {

    private val noIntent = { error("unlock intent must not be built") }

    @Test
    fun toStringRedactsSecret() {
        assertFalse(CredentialResult.ok("hunter2").toString().contains("hunter2"))
    }

    @Test
    fun foundMapsToOk() {
        val result = OwnedCredentialResult.Found("s").toCredentialResult(noIntent)
        assertEquals(CredentialResult.OK, result.status)
        assertEquals("s", result.secret)
    }

    @Test
    fun notFoundMapsToNotFound() {
        assertEquals(
            CredentialResult.NOT_FOUND,
            OwnedCredentialResult.NotFound.toCredentialResult(noIntent).status
        )
    }

    @Test
    fun failedKeepsRetryable() {
        val result = OwnedCredentialResult.Failed(retryable = false).toCredentialResult(noIntent)
        assertEquals(CredentialResult.ERROR, result.status)
        assertFalse(result.retryable)
    }
}
