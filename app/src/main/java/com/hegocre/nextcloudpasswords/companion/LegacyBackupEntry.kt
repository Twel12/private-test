package com.hegocre.nextcloudpasswords.companion

import com.hegocre.nextcloudpasswords.data.password.CustomField

// Backup keys saved before the companion API: recognised by their fixed fields, never rewritten.
internal object LegacyBackupEntry {
    val OWNER = Owner("foundation.e.backup", "murena-device-backup:v1")
    const val PIN_SETTING = "client.murena.backup.passwordId"

    private const val USERNAME = "Murena Backups"
    private const val LABEL = "Murena Device Backups"
    private const val URL = "android://foundation.e.backup"
    private const val NOTES = "BACKUP APP KEY: warning! don't modify manually"
    private const val MARKER_LABEL = "foundation.e.backup.key"
    private const val MARKER_VALUE = "murena-device-backup:v1"

    fun matches(entry: VaultEntry, owner: Owner): Boolean =
        owner == OWNER && matchesFields(entry.username, entry.label, entry.url, entry.notes, entry.customFields)

    fun matchesFields(
        username: String,
        label: String,
        url: String,
        notes: String,
        fields: List<CustomField>,
    ): Boolean = username == USERNAME && label == LABEL && url == URL && notes == NOTES &&
        fields.any { it.label == MARKER_LABEL && it.type == CustomField.TYPE_DATA && it.value == MARKER_VALUE }
}
