package com.hegocre.nextcloudpasswords.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinFingerprintTest {

    private val keyId = "6f1e2d3c-4b5a-4968-8776-655443322110"
    private val key = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
    private val otherId = "9a8b7c6d-5e4f-4321-8123-456789abcdef"
    private val otherKey = "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100"

    @Test
    fun `the same key and secret give the same fingerprint`() {
        assertEquals(PinFingerprint.of(keyId, key, "s3cret"), PinFingerprint.of(keyId, key, "s3cret"))
    }

    @Test
    fun `the fingerprint is a truncated HMAC under a key derived from the CSE key`() {
        // HMAC-SHA256(HMAC-SHA256(key, "murena-companion-fingerprint-v1"), "s3cret")[0..16], computed independently.
        assertEquals("6f1e2d3c:0d4c260348055f849c915d1ab690507b", PinFingerprint.of(keyId, key, "s3cret"))
    }

    @Test
    fun `a different secret or key gives a different fingerprint`() {
        val fp = PinFingerprint.of(keyId, key, "s3cret")

        assertNotEquals(fp, PinFingerprint.of(keyId, key, "s3cres"))
        assertNotEquals(fp, PinFingerprint.of(keyId, otherKey, "s3cret"))
    }

    @Test
    fun `the fingerprint is the key id prefix and 32 hex, without the secret`() {
        val secret = "correct-horse-battery"
        val fp = PinFingerprint.of(keyId, key, secret)!!

        assertTrue(fp.matches(Regex("^[0-9a-f-]{8}:[0-9a-f]{32}$")))
        assertEquals(keyId.take(8), fp.substringBefore(':'))
        assertFalse(fp.contains(secret))
        assertFalse(fp.contains(sha256Hex(secret).take(32)))
    }

    @Test
    fun `key material that is not hex gives no fingerprint`() {
        assertNull(PinFingerprint.of(keyId, "not-hex", "s3cret"))
        assertNull(PinFingerprint.of(keyId, "", "s3cret"))
    }

    @Test
    fun `a stored fingerprint matches its secret under any key with its prefix`() {
        val fp = PinFingerprint.of(keyId, key, "s3cret")!!
        val keys = mapOf(otherId to otherKey, keyId.take(8) + "-same-prefix" to otherKey, keyId to key)

        assertEquals(true, PinFingerprint.matches(fp, "s3cret", keys))
        assertEquals(false, PinFingerprint.matches(fp, "edited", keys))
    }

    @Test
    fun `a fingerprint whose key is gone or that is malformed cannot be checked`() {
        val fp = PinFingerprint.of(keyId, key, "s3cret")!!

        assertNull(PinFingerprint.matches(fp, "s3cret", mapOf(otherId to otherKey)))
        assertNull(PinFingerprint.matches("", "s3cret", mapOf(keyId to key)))
        assertNull(PinFingerprint.matches("no-colon", "s3cret", mapOf(keyId to key)))
        assertNull(PinFingerprint.matches("${keyId.take(8)}:xyz", "s3cret", mapOf(keyId to key)))
        assertNull(PinFingerprint.matches(":${"a".repeat(32)}", "s3cret", mapOf(keyId to key)))
    }
}
