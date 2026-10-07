package com.hegocre.nextcloudpasswords.backupApp

import com.hegocre.nextcloudpasswords.data.password.CustomField
import com.hegocre.nextcloudpasswords.data.password.Password
import kotlinx.serialization.json.Json

object BackupAppPassword {
    const val URI = "android://foundation.e.backup"
    const val LABEL = "Murena Device Backups"
    const val USERNAME = "Murena Backups"
    const val WARNING = "BACKUP APP KEY: warning! don't modify manually"
    private const val MARKER_LABEL = "foundation.e.backup.key"
    private const val MARKER_VALUE = "murena-device-backup:v1"


    fun matches(password: Password): Boolean {
        return password.username == USERNAME &&
            password.label == LABEL &&
            password.url == URI &&
            password.notes == WARNING &&
            hasMarker(password)
    }

    private fun hasMarker(password: Password): Boolean {
        return runCatching {
            Json.decodeFromString<List<CustomField>>(password.customFields)
        }.getOrNull()?.any { customField ->
            customField.label == MARKER_LABEL &&
                customField.type == CustomField.TYPE_DATA &&
                customField.value == MARKER_VALUE
        } == true
    }
}
