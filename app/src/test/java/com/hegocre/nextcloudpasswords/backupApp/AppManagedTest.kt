/*
 * Copyright (C) 2026 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hegocre.nextcloudpasswords.backupApp

import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillMetadata
import com.hegocre.nextcloudpasswords.services.autofill.excludeAppManaged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagedTest {

    @Test
    fun readOnlyFieldRoundTrips() {
        val readOnly = NCPAutofillMetadata.withReadOnly("[]", true)
        assertTrue(NCPAutofillMetadata.isReadOnly(readOnly))
        assertFalse(NCPAutofillMetadata.isReadOnly(NCPAutofillMetadata.withReadOnly(readOnly, false)))
    }

    @Test
    fun withReadOnlyKeepsOtherFieldsAndNeverDuplicates() {
        val withKey = NCPAutofillMetadata.withIdentityKey("[]", "k")
        val readOnly = NCPAutofillMetadata.withReadOnly(withKey, true)
        assertEquals("k", NCPAutofillMetadata.identityKey(readOnly))
        assertEquals(readOnly, NCPAutofillMetadata.withReadOnly(readOnly, true))
    }

    @Test
    fun readOnlyNeedsTheTrueValue() {
        val json = """[{"label":"foundation.e.credential.readonly","type":"data","value":"false"}]"""
        assertFalse(NCPAutofillMetadata.isReadOnly(json))
    }

    @Test
    fun readOnlyEntryIsAppManagedAndNotEditable() {
        val entry = testPassword("1", customFields = NCPAutofillMetadata.withReadOnly("[]", true))
        assertTrue(entry.isAppManaged())
        assertFalse(entry.canEdit())
    }

    @Test
    fun legacyBackupKeyIsAppManaged() {
        val entry = legacyBackupPassword("2")
        assertTrue(entry.isAppManaged())
        assertFalse(entry.canEdit())
    }

    @Test
    fun ordinaryEntryWithIdentityKeyStaysEditable() {
        val entry = testPassword("3", customFields = NCPAutofillMetadata.withIdentityKey("[]", "k"))
        assertFalse(entry.isAppManaged())
        assertTrue(entry.canEdit())
    }

    @Test
    fun excludeAppManagedDropsOnlyManagedEntries() {
        val entries = listOf(
            testPassword("1", customFields = NCPAutofillMetadata.withReadOnly("[]", true)),
            legacyBackupPassword("2"),
            testPassword("3"),
        )
        assertEquals(listOf("3"), entries.excludeAppManaged().map { it.id })
    }
}
