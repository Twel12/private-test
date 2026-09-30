package com.hegocre.nextcloudpasswords.backupApp

import com.hegocre.nextcloudpasswords.data.password.CustomField
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillMetadata
import com.hegocre.nextcloudpasswords.services.autofill.OwnedEntryTemplate
import kotlinx.serialization.json.Json

object BackupAppPassword {
    const val PACKAGE_NAME = "foundation.e.backup"
    const val URI = "android://$PACKAGE_NAME"
    const val LABEL = "Murena Device Backups"
    const val USERNAME = "Murena Backups"
    const val WARNING = "BACKUP APP KEY: warning! don't modify manually"
    private const val MARKER_LABEL = "foundation.e.backup.key"
    private const val MARKER_VALUE = "murena-device-backup:v1"
    const val IDENTITY_KEY = MARKER_VALUE

    private val markerCustomField = CustomField(
        label = MARKER_LABEL,
        type = CustomField.TYPE_DATA,
        value = MARKER_VALUE
    )

    private val customFieldsJson: String = Json.encodeToString(listOf(markerCustomField))

    val template = OwnedEntryTemplate(
        label = LABEL,
        url = URI,
        notes = WARNING,
        customFieldsJson = customFieldsJson,
        requireEndToEnd = true
    )

    fun isOwned(password: Password): Boolean = matches(password) || hasOwnerFields(password)

    private fun hasOwnerFields(password: Password): Boolean =
        password.customFields.contains(IDENTITY_KEY) &&
            NCPAutofillMetadata.identityKey(password.customFields) == IDENTITY_KEY &&
            PACKAGE_NAME in NCPAutofillMetadata.packageNames(password.customFields)

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
