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

import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ApiCompanionStoreTest {

    @Test
    fun `a keychain whose current key exists is used for writes`() {
        val keychain = CSEv1Keychain(mapOf("k1" to "aa"), "k1")
        assertSame(keychain, ApiCompanionStore.usableKeychain(keychain))
    }

    @Test
    fun `no keychain, a blank current key or a missing current key is never used for writes`() {
        assertNull(ApiCompanionStore.usableKeychain(null))
        assertNull(ApiCompanionStore.usableKeychain(CSEv1Keychain(mapOf("k1" to "aa"), "")))
        assertNull(ApiCompanionStore.usableKeychain(CSEv1Keychain(mapOf("k1" to "aa"), " ")))
        assertNull(ApiCompanionStore.usableKeychain(CSEv1Keychain(mapOf("k1" to "aa"), "k2")))
    }
}
