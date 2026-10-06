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

import com.hegocre.nextcloudpasswords.data.password.CustomField
import com.hegocre.nextcloudpasswords.data.password.Password
import foundation.e.passwords.companion.EntryPresentation
import kotlinx.serialization.json.Json

object OwnedEntry {
    const val ANDROID_APPS_FIELD = "Android apps"
    const val CREDENTIAL_ID_FIELD = "foundation.e.credential.key"

    private val RESERVED_FIELDS = setOf(ANDROID_APPS_FIELD, CREDENTIAL_ID_FIELD)
    private val json = Json { ignoreUnknownKeys = true }

    fun draft(owner: Owner, secret: String, presentation: EntryPresentation): EntryDraft = EntryDraft(
        label = presentation.label,
        username = presentation.username,
        secret = secret,
        url = "android://${owner.packageName}",
        notes = presentation.notes,
        customFields = presentation.extraDataFields
            .filterKeys { it !in RESERVED_FIELDS }
            .map { (label, value) -> CustomField(label, CustomField.TYPE_DATA, value) } +
            CustomField(ANDROID_APPS_FIELD, CustomField.TYPE_TEXT, owner.packageName) +
            CustomField(CREDENTIAL_ID_FIELD, CustomField.TYPE_DATA, owner.credentialId),
    )

    fun isOwnedBy(entry: VaultEntry, owner: Owner): Boolean =
        hasOwnerFields(entry.customFields, owner) || LegacyBackupEntry.matches(entry, owner)

    fun isAppOwned(password: Password): Boolean {
        val fields = parseFields(password.customFields)
        val hasOwnerFields = fields.any { it.label == CREDENTIAL_ID_FIELD && it.value.isNotBlank() } &&
            fields.any { it.label == ANDROID_APPS_FIELD && it.value.isNotBlank() }
        return hasOwnerFields || LegacyBackupEntry.matchesFields(
            password.username, password.label, password.url, password.notes, fields,
        )
    }

    fun parseFields(customFieldsJson: String): List<CustomField> =
        runCatching { json.decodeFromString<List<CustomField>>(customFieldsJson) }.getOrDefault(emptyList())

    private fun hasOwnerFields(fields: List<CustomField>, owner: Owner): Boolean =
        fields.any { it.label == CREDENTIAL_ID_FIELD && it.value.trim() == owner.credentialId } &&
            fields.any { field ->
                field.label == ANDROID_APPS_FIELD && field.value.lineSequence().any { it.trim() == owner.packageName }
            }
}
