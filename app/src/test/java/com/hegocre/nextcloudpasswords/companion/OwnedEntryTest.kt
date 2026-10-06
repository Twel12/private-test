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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedEntryTest {

    @Test
    fun `a draft keeps the app's presentation and adds the owner fields last`() {
        val draft = OwnedEntry.draft(
            BACKUP, "key",
            EntryPresentation(
                "Murena Device Backups", "Murena Backups", "note",
                mapOf("foundation.e.backup.key" to "murena-device-backup:v1"),
            ),
        )

        assertEquals("Murena Device Backups", draft.label)
        assertEquals("Murena Backups", draft.username)
        assertEquals("android://foundation.e.backup", draft.url)
        assertEquals(
            listOf(
                CustomField("foundation.e.backup.key", CustomField.TYPE_DATA, "murena-device-backup:v1"),
                CustomField("Android apps", CustomField.TYPE_TEXT, "foundation.e.backup"),
                CustomField("foundation.e.credential.key", CustomField.TYPE_DATA, "murena-device-backup:v1"),
            ),
            draft.customFields,
        )
    }

    @Test
    fun `an app cannot write the owner fields through its extra fields`() {
        val draft = OwnedEntry.draft(
            FMD, "code",
            EntryPresentation(
                "L", "U", "N",
                mapOf(
                    "Android apps" to "foundation.e.backup",
                    "foundation.e.credential.key" to "murena-device-backup:v1",
                ),
            ),
        )

        assertTrue(OwnedEntry.isOwnedBy(draft.toEntry("1"), FMD))
        assertFalse(OwnedEntry.isOwnedBy(draft.toEntry("1"), BACKUP))
        assertEquals(2, draft.customFields.size)
    }

    @Test
    fun `ownership needs both the package and the id`() {
        val entry = ownedEntry("1", FMD)

        assertTrue(OwnedEntry.isOwnedBy(entry, FMD))
        assertFalse(OwnedEntry.isOwnedBy(entry, FMD.copy(credentialId = "other")))
        assertFalse(OwnedEntry.isOwnedBy(entry, FMD.copy(packageName = "com.example")))
        assertFalse(OwnedEntry.isOwnedBy(plainEntry("2"), FMD))
    }

    @Test
    fun `a legacy backup entry belongs only to the backup owner`() {
        assertTrue(OwnedEntry.isOwnedBy(legacyBackupEntry("1"), BACKUP))
        assertFalse(OwnedEntry.isOwnedBy(legacyBackupEntry("1"), FMD))
    }

    @Test
    fun `an old FMD entry linked to several apps is still owned by FMD`() {
        val entry = plainEntry("1").copy(
            customFields = listOf(
                CustomField("Android apps", CustomField.TYPE_TEXT, "com.example\nfoundation.e.findmydevice"),
                CustomField("foundation.e.credential.key", CustomField.TYPE_DATA, FMD.credentialId),
            ),
        )

        assertTrue(OwnedEntry.isOwnedBy(entry, FMD))
        assertFalse(OwnedEntry.isOwnedBy(entry, FMD.copy(packageName = "com.example.other")))
        assertTrue(OwnedEntry.isAppOwned(password(entry)))
    }

    @Test
    fun `a backup key saved before the companion API is owned by the backup app`() {
        // Exactly what NCPPasswordBackend.toNewPassword wrote for BackupAppPassword.template (d0b96e4).
        val pr1Fields = "[" +
            """{"label":"foundation.e.backup.key","type":"data","value":"murena-device-backup:v1"},""" +
            """{"label":"Android apps","type":"text","value":"foundation.e.backup"},""" +
            """{"label":"foundation.e.credential.key","type":"data","value":"murena-device-backup:v1"}""" +
            "]"
        val entry = legacyBackupEntry("1").copy(customFields = OwnedEntry.parseFields(pr1Fields))
        val fieldsOnly = plainEntry("2").copy(customFields = OwnedEntry.parseFields(pr1Fields))

        assertEquals(3, entry.customFields.size)
        assertTrue(OwnedEntry.isOwnedBy(entry, BACKUP))
        assertTrue(OwnedEntry.isOwnedBy(fieldsOnly, BACKUP))
        assertFalse(OwnedEntry.isOwnedBy(entry, FMD))
        assertTrue(OwnedEntry.isAppOwned(password(entry)))
    }

    @Test
    fun `an entry with only the credential key field is not owned`() {
        val entry = plainEntry("1").copy(
            customFields = listOf(CustomField("foundation.e.credential.key", CustomField.TYPE_DATA, FMD.credentialId)),
        )

        assertFalse(OwnedEntry.isOwnedBy(entry, FMD))
        assertFalse(OwnedEntry.isAppOwned(password(entry)))
    }

    @Test
    fun `app-owned passwords are recognised, ordinary ones are not`() {
        assertTrue(OwnedEntry.isAppOwned(password(ownedEntry("1", FMD))))
        assertTrue(OwnedEntry.isAppOwned(password(legacyBackupEntry("2"))))
        assertFalse(OwnedEntry.isAppOwned(password(plainEntry("3"))))
        val linkedOnly = plainEntry("4").copy(
            customFields = listOf(CustomField("Android apps", CustomField.TYPE_TEXT, "com.bank")),
        )
        assertFalse(OwnedEntry.isAppOwned(password(linkedOnly)))
    }

    @Test
    fun `app-owned entries cannot be edited, ordinary ones can`() {
        assertFalse(password(ownedEntry("1", FMD)).canEdit())
        assertFalse(password(legacyBackupEntry("2")).canEdit())
        assertTrue(password(plainEntry("3")).canEdit())
    }

    @Test
    fun `entries and drafts never print their secret`() {
        assertFalse(ownedEntry("1", FMD, secret = "s3cret").toString().contains("s3cret"))
        assertFalse(
            OwnedEntry.draft(FMD, "s3cret", EntryPresentation("L", "U", "N")).toString().contains("s3cret"),
        )
    }

    private fun password(entry: VaultEntry) = Password(
        id = entry.id, label = entry.label, username = entry.username, password = entry.secret, url = entry.url,
        notes = entry.notes, customFields = Json.encodeToString(entry.customFields), status = 0, statusCode = "GOOD",
        hash = "", folder = entry.folder, revision = entry.revision, share = null, shared = false, cseType = "none",
        cseKey = "", sseType = "", client = "", hidden = false, trashed = false, favorite = false, editable = true,
        edited = 0, created = 0, updated = 0,
    )
}
