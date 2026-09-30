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
package com.hegocre.nextcloudpasswords.backupApp

import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillMetadata
import foundation.e.backupappapi.E2eeKeyWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeyLookupTest {

    @Test
    fun `a missing or deleted pin allows looking up and creating a key`() {
        assertNull(PinnedKey.NotSet.blockingResult())
        assertNull(PinnedKey.Deleted.blockingResult())
    }

    @Test
    fun `a readable pin is served, not blocked`() {
        assertNull(PinnedKey.Readable(backupEntry(customFields = "[]")).blockingResult())
    }

    @Test
    fun `a pin that cannot be read never leads to a new key`() {
        assertEquals(
            E2eeKeyWrapper.ApiError(shouldRetry = true),
            PinnedKey.ReadFailed(pinnedId = null).blockingResult(),
        )
        assertEquals(E2eeKeyWrapper.E2eeUnavailable(), PinnedKey.Unreadable.blockingResult())
        assertEquals(
            E2eeKeyWrapper.ApiError(shouldRetry = false),
            PinnedKey.Modified.blockingResult(),
        )
    }

    @Test
    fun `a new entry is still found by the legacy match of older versions`() {
        val entry = backupEntry(customFields = newEntryCustomFields())

        assertTrue(BackupAppPassword.matches(entry))
    }

    @Test
    fun `a new entry also carries the find my device owner fields`() {
        val customFields = newEntryCustomFields()

        assertEquals(BackupAppPassword.IDENTITY_KEY, NCPAutofillMetadata.identityKey(customFields))
        assertTrue(BackupAppPassword.PACKAGE_NAME in NCPAutofillMetadata.packageNames(customFields))
    }

    @Test
    fun `an entry made by older versions is the backup key`() {
        val entry = backupEntry(customFields = BackupAppPassword.template.customFieldsJson)

        assertTrue(BackupAppPassword.isOwned(entry))
    }

    @Test
    fun `a new entry stays the backup key after its label is edited`() {
        val entry = backupEntry(customFields = newEntryCustomFields(), label = "Renamed")

        assertFalse(BackupAppPassword.matches(entry))
        assertTrue(BackupAppPassword.isOwned(entry))
    }

    @Test
    fun `the identity key alone does not make another app's entry the backup key`() {
        val customFields = NCPAutofillMetadata.withIdentityKey(
            NCPAutofillMetadata.withPackage("[]", "com.example.other"),
            BackupAppPassword.IDENTITY_KEY,
        )

        assertFalse(BackupAppPassword.isOwned(backupEntry(customFields = customFields, label = "Other")))
    }

    private fun newEntryCustomFields(): String = NCPAutofillMetadata.withIdentityKey(
        NCPAutofillMetadata.withPackage(
            BackupAppPassword.template.customFieldsJson,
            BackupAppPassword.PACKAGE_NAME,
        ),
        BackupAppPassword.IDENTITY_KEY,
    )

    private fun backupEntry(
        customFields: String,
        label: String = BackupAppPassword.template.label,
    ) = Password(
        id = "00000000-0000-0000-0000-000000000001",
        label = label,
        username = BackupAppPassword.USERNAME,
        password = "key",
        url = BackupAppPassword.template.url,
        notes = BackupAppPassword.template.notes,
        customFields = customFields,
        status = 0,
        statusCode = "GOOD",
        hash = "",
        folder = "",
        revision = "",
        share = null,
        shared = false,
        cseType = "CSEv1r1",
        cseKey = "",
        sseType = "",
        client = "",
        hidden = false,
        trashed = false,
        favorite = false,
        editable = true,
        edited = 0,
        created = 0,
        updated = 0,
    )
}
