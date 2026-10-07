package com.hegocre.nextcloudpasswords.companion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class PinRecord(val v: Int = 1, val id: String, val fp: String? = null) {
    companion object {
        /** The Passwords server rejects user settings with longer keys or values (error 10002). */
        const val MAX_SETTING_KEY = 55
        const val MAX_SETTING_VALUE = 128

        private const val SETTING_PREFIX = "client.murena.companion."
        private const val KEY_HEX_LENGTH = 24
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun settingKey(owner: Owner): String =
            SETTING_PREFIX + sha256Hex("${owner.packageName}\n${owner.credentialId}").take(KEY_HEX_LENGTH)

        @Suppress("ReturnCount")
        fun parse(value: String?): PinRecord? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null
            if (text.startsWith("{")) return runCatching { json.decodeFromString<PinRecord>(text) }.getOrNull()
            return if (runCatching { UUID.fromString(text) }.isSuccess) PinRecord(id = text) else null
        }

        fun encode(record: PinRecord): String = json.encodeToString(serializer(), record)
    }
}

internal fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
