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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class PinRecord(val v: Int = 1, val id: String, val fp: String? = null) {
    companion object {
        private const val SETTING_PREFIX = "client.murena.companion."
        private const val KEY_HEX_LENGTH = 32
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
