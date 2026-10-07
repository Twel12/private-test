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
