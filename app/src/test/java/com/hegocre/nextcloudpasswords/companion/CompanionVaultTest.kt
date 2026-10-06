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

import foundation.e.passwords.companion.EntryPresentation
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.SaveMode
import foundation.e.passwords.companion.UserAction
import foundation.e.passwords.companion.UserReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionVaultTest {

    private val store = FakeStore()
    private val gate = FakeGate()
    private val failedPinWrites = mutableListOf<String>()
    private val vault = CompanionVault(store, gate) { failedPinWrites += it }
    private val presentation = EntryPresentation("Label", "User", "Notes")

    private fun pinOf(owner: Owner) = PinRecord.parse(store.settings[PinRecord.settingKey(owner)])

    @Test
    fun `no pin and one owned entry is found and pinned`() = runBlocking {
        store.add(ownedEntry("a", FMD, secret = "code"))
        store.add(plainEntry("b"))

        assertEquals(VaultOutcome.Found("code", 1L), vault.get(FMD))
        assertEquals("a", pinOf(FMD)?.id)
        assertEquals(fakeFp("code"), pinOf(FMD)?.fp)
    }

    @Test
    fun `nothing owned and everything readable is not found`() = runBlocking {
        store.add(plainEntry("b"))
        assertEquals(VaultOutcome.NotFound, vault.get(FMD))
    }

    @Test
    fun `nothing owned but something unreadable is never not found`() = runBlocking {
        store.undecryptable = 1
        assertEquals(VaultOutcome.Failed(FailureCode.UNREADABLE), vault.get(FMD))
    }

    @Test
    fun `two owned entries ask the user to choose`() = runBlocking {
        store.add(ownedEntry("a", FMD))
        store.add(ownedEntry("b", FMD))
        assertEquals(VaultOutcome.NeedsUser(UserAction.CHOOSE_ENTRY, UserReason.CONFLICT), vault.get(FMD))
    }

    @Test
    fun `another app's entry with the same id is invisible`() = runBlocking {
        store.add(ownedEntry("a", Owner("com.example.other", FMD.credentialId)))
        assertEquals(VaultOutcome.NotFound, vault.get(FMD))
    }

    @Test
    fun `a pinned entry in the trash is restored and served`() = runBlocking {
        store.add(ownedEntry("a", FMD, secret = "code").copy(trashed = true))
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "a"))

        assertEquals(VaultOutcome.Found("code", 1L), vault.get(FMD))
        assertFalse(store.entries.getValue("a").trashed)
    }

    @Test
    fun `a pinned entry that no longer looks owned is modified, never looked up again`() = runBlocking {
        store.add(plainEntry("a"))
        store.add(ownedEntry("b", FMD))
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "a"))

        assertEquals(VaultOutcome.Failed(FailureCode.MODIFIED), vault.get(FMD))
    }

    @Test
    fun `a pinned entry whose secret changed is modified`() = runBlocking {
        store.add(ownedEntry("a", FMD, secret = "edited"))
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "a", fp = fakeFp("original")))

        assertEquals(VaultOutcome.Failed(FailureCode.MODIFIED), vault.get(FMD))
    }

    @Test
    fun `a pinned fingerprint made with a key no longer in the keychain is unreadable`() = runBlocking {
        store.add(ownedEntry("a", FMD, secret = "code"))
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "a", fp = "k9:${"0".repeat(32)}"))

        assertEquals(VaultOutcome.Failed(FailureCode.UNREADABLE), vault.get(FMD))
    }

    @Test
    fun `a pinned entry that cannot be decrypted is unreadable`() = runBlocking {
        store.add(ownedEntry("a", FMD))
        store.unreadableIds += "a"
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "a"))

        assertEquals(VaultOutcome.Failed(FailureCode.UNREADABLE), vault.get(FMD))
    }

    @Test
    fun `a deleted pinned entry falls back to the lookup`() = runBlocking {
        store.add(ownedEntry("b", FMD, secret = "code"))
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "gone"))

        assertEquals(VaultOutcome.Found("code", 1L), vault.get(FMD))
        assertEquals("b", pinOf(FMD)?.id)
    }

    @Test
    fun `a pin that cannot be read is a retryable failure`() = runBlocking {
        store.settingError = FailureCode.NETWORK
        assertEquals(VaultOutcome.Failed(FailureCode.NETWORK), vault.get(FMD))
    }

    @Test
    fun `the legacy backup pin is honoured and upgraded`() = runBlocking {
        val id = "3f1c2a4e-0000-4000-8000-000000000001"
        store.add(legacyBackupEntry(id, secret = "backup-key").copy(trashed = true))
        store.settings[LegacyBackupEntry.PIN_SETTING] = id

        assertEquals(VaultOutcome.Found("backup-key", 1L), vault.get(BACKUP))
        assertFalse(store.entries.getValue(id).trashed)
        assertEquals(id, pinOf(BACKUP)?.id)
    }

    @Test
    fun `a legacy backup entry is found by lookup and left untouched`() = runBlocking {
        val legacy = legacyBackupEntry("k", secret = "backup-key")
        store.add(legacy)

        assertEquals(VaultOutcome.Found("backup-key", 1L), vault.get(BACKUP))
        assertEquals(legacy, store.entries.getValue("k"))
    }

    @Test
    fun `create only never overwrites`() = runBlocking {
        store.add(ownedEntry("a", BACKUP, secret = "old"))

        assertEquals(VaultOutcome.AlreadyExists, vault.save(BACKUP, "new", SaveMode.CREATE_ONLY, presentation))
        assertEquals("old", store.entries.getValue("a").secret)
    }

    @Test
    fun `create only creates the entry with the app's presentation and pins it`() = runBlocking {
        assertEquals(VaultOutcome.Saved(created = true), vault.save(BACKUP, "new", SaveMode.CREATE_ONLY, presentation))

        val created = store.entries.values.single()
        assertEquals("Label", created.label)
        assertEquals("new", created.secret)
        assertTrue(OwnedEntry.isOwnedBy(created, BACKUP))
        assertEquals(created.id, pinOf(BACKUP)?.id)
    }

    @Test
    fun `create only creates nothing when the lookup is inconclusive`() = runBlocking {
        store.listError = FailureCode.NETWORK

        assertEquals(VaultOutcome.Failed(FailureCode.NETWORK), vault.save(BACKUP, "new", SaveMode.CREATE_ONLY, presentation))
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `replace updates the secret and the pin fingerprint`() = runBlocking {
        store.add(ownedEntry("a", FMD, secret = "old"))

        assertEquals(VaultOutcome.Saved(created = false), vault.save(FMD, "new", SaveMode.REPLACE, presentation))
        assertEquals("new", store.entries.getValue("a").secret)
        assertEquals(fakeFp("new"), pinOf(FMD)?.fp)
    }

    @Test
    fun `replace creates when nothing exists`() = runBlocking {
        assertEquals(VaultOutcome.Saved(created = true), vault.save(FMD, "code", SaveMode.REPLACE, presentation))
    }

    @Test
    fun `delete trashes the entry and clears the pins`() = runBlocking {
        store.add(legacyBackupEntry("k"))
        store.settings[LegacyBackupEntry.PIN_SETTING] = "k"

        assertEquals(VaultOutcome.Deleted, vault.delete(BACKUP))
        assertTrue(store.entries.getValue("k").trashed)
        assertNull(pinOf(BACKUP))
        assertEquals("", store.settings[LegacyBackupEntry.PIN_SETTING])
    }

    @Test
    fun `a blocked session touches nothing`() = runBlocking {
        gate.blocked = GateResult.Blocked(UserAction.UNLOCK_ON_DEVICE, UserReason.VAULT_LOCKED)

        assertEquals(
            VaultOutcome.NeedsUser(UserAction.UNLOCK_ON_DEVICE, UserReason.VAULT_LOCKED),
            vault.save(FMD, "code", SaveMode.REPLACE, presentation),
        )
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `choosing keeps one entry, trashes the rest and pins it`() = runBlocking {
        store.add(ownedEntry("a", FMD))
        store.add(ownedEntry("b", FMD))

        assertEquals(2, vault.candidates(FMD).size)
        assertTrue(vault.choose(FMD, "b"))
        assertTrue(store.entries.getValue("a").trashed)
        assertEquals("b", pinOf(FMD)?.id)
        assertEquals(VaultOutcome.Found("secret-b", 1L), vault.get(FMD))
    }

    @Test
    fun `create only creates nothing when an entry could not be decrypted`() = runBlocking {
        store.undecryptable = 1

        assertEquals(VaultOutcome.Failed(FailureCode.UNREADABLE), vault.save(BACKUP, "new", SaveMode.CREATE_ONLY, presentation))
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `a non-backup owner ignores the legacy backup pin`() = runBlocking {
        val backupId = "3f1c2a4e-0000-4000-8000-000000000002"
        store.add(legacyBackupEntry(backupId, secret = "backup-key"))
        store.settings[LegacyBackupEntry.PIN_SETTING] = backupId
        store.add(ownedEntry("a", FMD, secret = "code"))

        assertEquals(VaultOutcome.Found("code", 1L), vault.get(FMD))
        assertEquals("a", pinOf(FMD)?.id)
    }

    @Test
    fun `a pinned show error is passed through without lookup or create`() = runBlocking {
        store.add(ownedEntry("b", FMD))
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "a"))
        store.showError = FailureCode.SERVER

        assertEquals(VaultOutcome.Failed(FailureCode.SERVER), vault.get(FMD))
        assertEquals(VaultOutcome.Failed(FailureCode.SERVER), vault.save(FMD, "x", SaveMode.REPLACE, presentation))
        assertEquals(setOf("b"), store.entries.keys)
    }

    @Test
    fun `a failed restore of a pinned trashed entry fails and creates nothing`() = runBlocking {
        store.add(ownedEntry("a", FMD).copy(trashed = true))
        store.settings[PinRecord.settingKey(FMD)] = PinRecord.encode(PinRecord(id = "a"))
        store.restoreFails = true

        assertEquals(VaultOutcome.Failed(FailureCode.SERVER), vault.save(FMD, "x", SaveMode.REPLACE, presentation))
        assertEquals(setOf("a"), store.entries.keys)
    }

    @Test
    fun `a conflict stops save and delete before any write`() = runBlocking {
        store.add(ownedEntry("a", FMD))
        store.add(ownedEntry("b", FMD))
        val conflict = VaultOutcome.NeedsUser(UserAction.CHOOSE_ENTRY, UserReason.CONFLICT)

        assertEquals(conflict, vault.save(FMD, "x", SaveMode.CREATE_ONLY, presentation))
        assertEquals(conflict, vault.save(FMD, "x", SaveMode.REPLACE, presentation))
        assertEquals(conflict, vault.delete(FMD))
        assertEquals(setOf("a", "b"), store.entries.keys)
        assertFalse(store.entries.values.any { it.trashed })
        assertEquals("secret-a", store.entries.getValue("a").secret)
    }

    @Test
    fun `delete with nothing found is not found and creates nothing`() = runBlocking {
        assertEquals(VaultOutcome.NotFound, vault.delete(FMD))
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `a failed gate is a failed outcome`() = runBlocking {
        gate.failed = GateResult.Failed(FailureCode.NETWORK)

        assertEquals(VaultOutcome.Failed(FailureCode.NETWORK), vault.get(FMD))
        assertEquals(VaultOutcome.Failed(FailureCode.NETWORK), vault.delete(FMD))
    }

    @Test
    fun `replace writes nothing when the fingerprint-free pin cannot be written first`() = runBlocking {
        store.add(ownedEntry("a", FMD, secret = "old"))
        store.failingWrites += PinRecord.settingKey(FMD)

        assertEquals(VaultOutcome.Failed(FailureCode.NETWORK), vault.save(FMD, "new", SaveMode.REPLACE, presentation))
        assertEquals("old", store.entries.getValue("a").secret)
    }

    @Test
    fun `replace recovers when the pin write after the update fails`() = runBlocking {
        store.add(ownedEntry("a", FMD, secret = "old"))
        store.settings[PinRecord.settingKey(FMD)] =
            PinRecord.encode(PinRecord(id = "a", fp = fakeFp("old")))
        store.failAfterUpdate = true

        assertEquals(VaultOutcome.Saved(created = false), vault.save(FMD, "new", SaveMode.REPLACE, presentation))
        assertEquals("new", store.entries.getValue("a").secret)
        assertNull(pinOf(FMD)?.fp)
        assertEquals(listOf(PinRecord.settingKey(FMD)), failedPinWrites)
        store.failingWrites.clear()
        assertEquals(VaultOutcome.Found("new", 1L), vault.get(FMD))
        assertEquals(fakeFp("new"), pinOf(FMD)?.fp)
    }

    @Test
    fun `a pin that cannot be written is reported by its setting key and the entry is still found`() =
        runBlocking {
            store.add(ownedEntry("a", FMD, secret = "code"))
            store.failingWrites += PinRecord.settingKey(FMD)

            assertEquals(VaultOutcome.Found("code", 1L), vault.get(FMD))
            assertEquals(listOf(PinRecord.settingKey(FMD)), failedPinWrites)
        }

    @Test
    fun `delete trashes nothing when a pin cannot be cleared`() = runBlocking {
        store.add(legacyBackupEntry("k"))
        store.settings[LegacyBackupEntry.PIN_SETTING] = "k"
        store.failingWrites += LegacyBackupEntry.PIN_SETTING

        assertEquals(VaultOutcome.Failed(FailureCode.NETWORK), vault.delete(BACKUP))
        assertFalse(store.entries.getValue("k").trashed)
    }

    @Test
    fun `delete trashes nothing when the generic pin cannot be cleared`() = runBlocking {
        store.add(ownedEntry("a", FMD))
        store.failingWrites += PinRecord.settingKey(FMD)

        assertEquals(VaultOutcome.Failed(FailureCode.NETWORK), vault.delete(FMD))
        assertFalse(store.entries.getValue("a").trashed)
    }

    @Test
    fun `found never prints its secret`() {
        assertFalse(VaultOutcome.Found("s3cret", 1L).toString().contains("s3cret"))
    }
}

private class FakeStore : CompanionStore {
    val entries = linkedMapOf<String, VaultEntry>()
    val settings = mutableMapOf<String, String>()
    val unreadableIds = mutableSetOf<String>()
    var undecryptable = 0
    var listError: String? = null
    var settingError: String? = null
    var showError: String? = null
    var restoreFails = false
    var failAfterUpdate = false
    val failingWrites = mutableSetOf<String>()
    private var nextId = 1

    fun add(entry: VaultEntry) {
        entries[entry.id] = entry
    }

    override suspend fun show(id: String): StoreRead<VaultEntry> = when {
        showError != null -> StoreRead.Error(showError!!)
        id in unreadableIds -> StoreRead.Unreadable
        else -> entries[id]?.let { StoreRead.Ok(it) } ?: StoreRead.NotFound
    }

    override suspend fun listAll(): StoreList = listError?.let { StoreList.Error(it) }
        ?: StoreList.Ok(entries.values.filterNot { it.trashed }, undecryptable)

    override suspend fun create(draft: EntryDraft): Boolean {
        val id = "new-${nextId++}"
        entries[id] = draft.toEntry(id)
        return true
    }

    override suspend fun update(entry: VaultEntry, draft: EntryDraft): Boolean {
        entries[entry.id] = draft.toEntry(entry.id, entry.created)
        if (failAfterUpdate) failingWrites += settings.keys.toList()
        return true
    }

    override suspend fun trash(entry: VaultEntry): Boolean {
        entries[entry.id] = entry.copy(trashed = true)
        return true
    }

    override suspend fun restore(id: String): Boolean {
        val entry = entries[id] ?: return false
        if (restoreFails) return false
        entries[id] = entry.copy(trashed = false)
        return true
    }

    override suspend fun readSetting(key: String): StoreRead<String?> =
        settingError?.let { StoreRead.Error(it) } ?: StoreRead.Ok(settings[key])

    override suspend fun writeSetting(key: String, value: String): Boolean {
        if (key in failingWrites) return false
        settings[key] = value
        return true
    }

    override fun fingerprint(secret: String): String = fakeFp(secret)

    override fun fingerprintMatches(stored: String, secret: String): Boolean? =
        if (stored.startsWith(FAKE_KEY_PREFIX)) stored == fakeFp(secret) else null
}

private const val FAKE_KEY_PREFIX = "k1:"

private fun fakeFp(secret: String) = FAKE_KEY_PREFIX + sha256Hex(secret).take(32)

private class FakeGate : SessionGate {
    var blocked: GateResult.Blocked? = null
    var failed: GateResult.Failed? = null

    override suspend fun <T> withSession(block: suspend () -> T): GateResult<T> =
        blocked ?: failed ?: GateResult.Open(block())
}
