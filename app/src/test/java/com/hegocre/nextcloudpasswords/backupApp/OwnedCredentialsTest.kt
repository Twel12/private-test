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
import foundation.e.backupappapi.PasswordsApp
import foundation.e.autofill.PasswordSaveResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedCredentialsTest {

    private fun owned(packageName: String, key: String) =
        NCPAutofillMetadata.withIdentityKey(NCPAutofillMetadata.withPackage("[]", packageName), key)

    @Test
    fun findsEntryWithMatchingKeyAndPackage() {
        val entry = testPassword("1", customFields = owned("app.a", "k"))
        assertEquals("1", selectOwnedEntry(listOf(testPassword("0"), entry), "app.a", "k")?.id)
    }

    @Test
    fun sameKeyFromAnotherPackageIsIgnored() {
        val entry = testPassword("1", customFields = owned("app.a", "k"))
        assertNull(selectOwnedEntry(listOf(entry), "app.b", "k"))
    }

    @Test
    fun trashedAndHiddenEntriesAreIgnored() {
        val trashed = testPassword("1", customFields = owned("app.a", "k"), trashed = true)
        val hidden = testPassword("2", customFields = owned("app.a", "k"), hidden = true)
        assertNull(selectOwnedEntry(listOf(trashed, hidden), "app.a", "k"))
    }

    @Test
    fun mostRecentlyEditedWinsWhenSeveralMatch() {
        val older = testPassword("1", customFields = owned("app.a", "k"), edited = 10)
        val newer = testPassword("2", customFields = owned("app.a", "k"), edited = 20)
        assertEquals("2", selectOwnedEntry(listOf(older, newer), "app.a", "k")?.id)
    }

    @Test
    fun legacyEntryOnlyForBackupCallerAndKey() {
        val legacy = listOf(legacyBackupPassword("1"))
        assertEquals("1", selectOwnedEntry(legacy, PasswordsApp.BACKUP_APP_PACKAGE, PasswordsApp.BACKUP_KEY)?.id)
        assertNull(selectOwnedEntry(legacy, "foundation.e.findmydevice", PasswordsApp.BACKUP_KEY))
        assertNull(selectOwnedEntry(legacy, PasswordsApp.BACKUP_APP_PACKAGE, "other-key"))
    }

    @Test
    fun ownedEntryWinsOverLegacy() {
        val legacy = legacyBackupPassword("1", edited = 99)
        val owned = testPassword("2", customFields = owned(PasswordsApp.BACKUP_APP_PACKAGE, PasswordsApp.BACKUP_KEY))
        assertEquals(
            "2",
            selectOwnedEntry(listOf(legacy, owned), PasswordsApp.BACKUP_APP_PACKAGE, PasswordsApp.BACKUP_KEY)?.id
        )
    }

    @Test
    fun hitDoesNotSync() = runBlocking {
        var synced = false
        val result = lookupWithSync(lookup = { testPassword("1") }, sync = { synced = true; true })
        assertTrue(result is OwnedLookup.Hit)
        assertFalse(synced)
    }

    @Test
    fun missSyncsAndLooksAgain() = runBlocking {
        var calls = 0
        val result = lookupWithSync(
            lookup = { if (calls++ == 0) null else testPassword("1") },
            sync = { true },
        )
        assertEquals("1", (result as OwnedLookup.Hit).password.id)
    }

    @Test
    fun syncFailureIsNeverAMiss() = runBlocking {
        assertEquals(OwnedLookup.SyncFailed, lookupWithSync(lookup = { null }, sync = { false }))
    }

    @Test
    fun missAfterSuccessfulSync() = runBlocking {
        assertEquals(OwnedLookup.Miss, lookupWithSync(lookup = { null }, sync = { true }))
    }

    @Test
    fun foundRedactsSecret() {
        assertFalse(OwnedCredentialResult.Found("hunter2").toString().contains("hunter2"))
    }

    @Test
    fun saveWithoutExistingEntryCreates() {
        assertEquals(OwnedSave.Create, ownedSaveDecision(null, "s", "u", replace = false, readOnly = true))
    }

    @Test
    fun saveWithoutReplaceReturnsStoredSecret() {
        val existing = testPassword("1", password = "stored")
        assertEquals(
            OwnedSave.ReturnStored("stored"),
            ownedSaveDecision(existing, "new", "user", replace = false, readOnly = true)
        )
    }

    @Test
    fun replaceWithSameValuesWritesNothing() {
        val existing = testPassword(
            "1",
            password = "s",
            username = "u",
            customFields = NCPAutofillMetadata.withReadOnly("[]", true)
        )
        assertEquals(
            OwnedSave.ReturnStored("s"),
            ownedSaveDecision(existing, "s", "u", replace = true, readOnly = true)
        )
    }

    @Test
    fun replaceWithChangedSecretOrFlagUpdates() {
        val existing = testPassword("1", password = "s", username = "u")
        assertEquals(OwnedSave.Update, ownedSaveDecision(existing, "new", "u", replace = true, readOnly = false))
        assertEquals(OwnedSave.Update, ownedSaveDecision(existing, "s", "u", replace = true, readOnly = true))
    }

    @Test
    fun saveResultsMapToOwnedResults() {
        assertEquals(OwnedCredentialResult.Found("s"), PasswordSaveResult.Saved.toOwnedResult("s"))
        assertEquals(OwnedCredentialResult.Found("s"), PasswordSaveResult.DuplicateIgnored.toOwnedResult("s"))
        assertEquals(OwnedCredentialResult.Locked, PasswordSaveResult.NeedsUnlock.toOwnedResult("s"))
        assertEquals(
            OwnedCredentialResult.Failed(retryable = true),
            PasswordSaveResult.Failed("x").toOwnedResult("s")
        )
        assertEquals(
            OwnedCredentialResult.Failed(retryable = true),
            PasswordSaveResult.NeedsUserInteraction("x").toOwnedResult("s")
        )
    }

    @Test
    fun ownedEntriesAreNeverCreatedWhenServerDisablesEncryption() {
        assertFalse(ownedCreateAllowed(encryptionCse = 0))
        assertTrue(ownedCreateAllowed(encryptionCse = 1))
        assertTrue(ownedCreateAllowed(encryptionCse = null))
    }
}
