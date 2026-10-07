package com.hegocre.nextcloudpasswords.companion

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A short keyed MAC of a companion secret, small enough for a pin setting: "<key id prefix>:<32 hex>".
 * The MAC key is derived from the CSEv1 key bytes, so the server never sees anything it can test guesses against.
 */
internal object PinFingerprint {
    private const val ALGORITHM = "HmacSHA256"
    private const val DOMAIN = "murena-companion-fingerprint-v1"
    private const val KEY_ID_PREFIX_LENGTH = 8
    private const val MAC_BYTES = 16
    private const val HEX_RADIX = 16
    private val HEX = Regex("^[0-9a-fA-F]*$")
    private val MAC_HEX = Regex("^[0-9a-f]{${MAC_BYTES * 2}}$")

    /** Null when the key material is not usable hex. */
    fun of(keyId: String, keyHex: String, secret: String): String? {
        val key = keyBytes(keyHex) ?: return null
        return "${keyId.take(KEY_ID_PREFIX_LENGTH)}:${mac(key, secret).toHex()}"
    }

    /**
     * True when any key whose id has the stored prefix gives the same MAC, false when none does,
     * null when no such key is in [keys] or [stored] is malformed.
     */
    @Suppress("ReturnCount")
    fun matches(stored: String, secret: String, keys: Map<String, String>): Boolean? {
        val prefix = stored.substringBefore(':', "")
        val macHex = stored.substringAfter(':', "")
        if (prefix.isEmpty() || prefix.length > KEY_ID_PREFIX_LENGTH || !macHex.matches(MAC_HEX)) return null
        val expected = macHex.hexToBytes() ?: return null
        val candidates = keys.filterKeys { it.take(KEY_ID_PREFIX_LENGTH) == prefix }.values.mapNotNull(::keyBytes)
        if (candidates.isEmpty()) return null
        return candidates.any { MessageDigest.isEqual(mac(it, secret), expected) }
    }

    private fun mac(key: ByteArray, secret: String): ByteArray {
        val macKey = hmac(key, DOMAIN.toByteArray(Charsets.UTF_8))
        return hmac(macKey, secret.toByteArray(Charsets.UTF_8)).copyOf(MAC_BYTES)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }.doFinal(data)

    // The keychain stores keys as hex; these are the same bytes encryptValue() hands to libsodium.
    private fun keyBytes(hex: String): ByteArray? = hex.takeIf { it.isNotEmpty() }?.hexToBytes()

    private fun String.hexToBytes(): ByteArray? =
        takeIf { length % 2 == 0 && matches(HEX) }?.chunked(2)?.map { it.toInt(HEX_RADIX).toByte() }?.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
