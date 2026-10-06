package com.hegocre.nextcloudpasswords.companion

import foundation.e.passwords.companion.EntryPresentation
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.SaveMode
import foundation.e.passwords.companion.UserAction
import foundation.e.passwords.companion.UserReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

sealed interface VaultOutcome {
    data class Found(val secret: String, val createdAt: Long) : VaultOutcome {
        override fun toString() = "Found(createdAt=$createdAt)"
    }
    data object NotFound : VaultOutcome
    data class Saved(val created: Boolean) : VaultOutcome
    data object AlreadyExists : VaultOutcome
    data object Deleted : VaultOutcome
    data class NeedsUser(val action: String, val reason: String) : VaultOutcome
    data class Failed(val code: String) : VaultOutcome
}

class CompanionVault(
    private val store: CompanionStore,
    private val gate: SessionGate,
) {
    private val locks = ConcurrentHashMap<Owner, Mutex>()

    suspend fun get(owner: Owner): VaultOutcome = guarded(owner) {
        when (val resolved = resolve(owner)) {
            is Resolution.Found -> VaultOutcome.Found(resolved.entry.secret, resolved.entry.created)
            Resolution.NotFound -> VaultOutcome.NotFound
            Resolution.Conflict -> CONFLICT
            is Resolution.Failed -> VaultOutcome.Failed(resolved.code)
        }
    }

    suspend fun save(
        owner: Owner,
        secret: String,
        mode: SaveMode,
        presentation: EntryPresentation,
    ): VaultOutcome = guarded(owner) {
        val draft = OwnedEntry.draft(owner, secret, presentation)
        when (val resolved = resolve(owner)) {
            is Resolution.Found ->
                if (mode == SaveMode.CREATE_ONLY) VaultOutcome.AlreadyExists else replace(owner, resolved.entry, draft)
            Resolution.NotFound -> create(owner, draft)
            Resolution.Conflict -> CONFLICT
            is Resolution.Failed -> VaultOutcome.Failed(resolved.code)
        }
    }

    suspend fun delete(owner: Owner): VaultOutcome = guarded(owner) {
        when (val resolved = resolve(owner)) {
            is Resolution.Found ->
                if (store.trash(resolved.entry)) {
                    clearPins(owner)
                    VaultOutcome.Deleted
                } else {
                    VaultOutcome.Failed(FailureCode.SERVER)
                }
            Resolution.NotFound -> VaultOutcome.NotFound
            Resolution.Conflict -> CONFLICT
            is Resolution.Failed -> VaultOutcome.Failed(resolved.code)
        }
    }

    suspend fun candidates(owner: Owner): List<VaultEntry> {
        val result = gate.withSession { ownedEntries(owner) }
        return (result as? GateResult.Open)?.value.orEmpty()
    }

    suspend fun choose(owner: Owner, keepId: String): Boolean {
        val result = gate.withSession {
            lockFor(owner).withLock {
                val owned = ownedEntries(owner)
                val keep = owned.firstOrNull { it.id == keepId } ?: return@withLock false
                if (owned.filter { it.id != keepId }.any { !store.trash(it) }) return@withLock false
                writePin(owner, keep.id, keep.secret)
                true
            }
        }
        return (result as? GateResult.Open)?.value == true
    }

    private suspend fun guarded(owner: Owner, block: suspend () -> VaultOutcome): VaultOutcome =
        when (val result = gate.withSession { lockFor(owner).withLock { block() } }) {
            is GateResult.Open -> result.value
            is GateResult.Blocked -> VaultOutcome.NeedsUser(result.action, result.reason)
            is GateResult.Failed -> VaultOutcome.Failed(result.code)
        }

    private fun lockFor(owner: Owner): Mutex = locks.computeIfAbsent(owner) { Mutex() }

    private suspend fun ownedEntries(owner: Owner): List<VaultEntry> =
        (store.listAll() as? StoreList.Ok)?.entries.orEmpty().filter { OwnedEntry.isOwnedBy(it, owner) }

    @Suppress("ReturnCount")
    private suspend fun resolve(owner: Owner): Resolution {
        val pin = when (val read = readPin(owner)) {
            is PinRead.Failed -> return Resolution.Failed(read.code)
            is PinRead.Value -> read
        }
        val record = pin.record ?: return lookup(owner)
        return when (val shown = store.show(record.id)) {
            StoreRead.NotFound -> lookup(owner)
            StoreRead.Unreadable -> Resolution.Failed(FailureCode.UNREADABLE)
            is StoreRead.Error -> Resolution.Failed(shown.code)
            is StoreRead.Ok -> checkPinned(owner, record, pin.legacy, shown.value)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun checkPinned(owner: Owner, record: PinRecord, legacy: Boolean, pinned: VaultEntry): Resolution {
        val entry = if (pinned.trashed) {
            if (!store.restore(pinned.id)) return Resolution.Failed(FailureCode.SERVER)
            (store.show(pinned.id) as? StoreRead.Ok)?.value ?: return Resolution.Failed(FailureCode.SERVER)
        } else {
            pinned
        }
        // A pinned entry that stopped looking owned was edited; a lookup could then find nothing
        // and lead the app to create a new secret.
        if (!OwnedEntry.isOwnedBy(entry, owner)) return Resolution.Failed(FailureCode.MODIFIED)
        if (record.fp != null) {
            val stored = store.openFingerprint(record.fp) ?: return Resolution.Failed(FailureCode.UNREADABLE)
            if (stored != sha256Hex(entry.secret)) return Resolution.Failed(FailureCode.MODIFIED)
        }
        if (legacy || record.fp == null) writePin(owner, entry.id, entry.secret)
        return Resolution.Found(entry)
    }

    private suspend fun lookup(owner: Owner): Resolution = when (val listed = store.listAll()) {
        is StoreList.Error -> Resolution.Failed(listed.code)
        is StoreList.Ok -> {
            val owned = listed.entries.filter { !it.trashed && OwnedEntry.isOwnedBy(it, owner) }
            when {
                owned.size == 1 -> owned.single().let {
                    writePin(owner, it.id, it.secret)
                    Resolution.Found(it)
                }
                owned.size > 1 -> Resolution.Conflict
                listed.undecryptable > 0 -> Resolution.Failed(FailureCode.UNREADABLE)
                else -> Resolution.NotFound
            }
        }
    }

    private suspend fun create(owner: Owner, draft: EntryDraft): VaultOutcome {
        if (!store.create(draft)) return VaultOutcome.Failed(FailureCode.SERVER)
        lookup(owner)
        return VaultOutcome.Saved(created = true)
    }

    private suspend fun replace(owner: Owner, entry: VaultEntry, draft: EntryDraft): VaultOutcome {
        if (!store.update(entry, draft)) return VaultOutcome.Failed(FailureCode.SERVER)
        writePin(owner, entry.id, draft.secret)
        return VaultOutcome.Saved(created = false)
    }

    @Suppress("ReturnCount")
    private suspend fun readPin(owner: Owner): PinRead {
        when (val read = store.readSetting(PinRecord.settingKey(owner))) {
            is StoreRead.Ok -> PinRecord.parse(read.value)?.let { return PinRead.Value(it, legacy = false) }
            is StoreRead.Error -> return PinRead.Failed(read.code)
            StoreRead.NotFound, StoreRead.Unreadable -> Unit
        }
        if (owner != LegacyBackupEntry.OWNER) return PinRead.Value(null, legacy = false)
        return when (val legacy = store.readSetting(LegacyBackupEntry.PIN_SETTING)) {
            is StoreRead.Ok -> PinRead.Value(PinRecord.parse(legacy.value), legacy = true)
            is StoreRead.Error -> PinRead.Failed(legacy.code)
            StoreRead.NotFound, StoreRead.Unreadable -> PinRead.Value(null, legacy = false)
        }
    }

    private suspend fun writePin(owner: Owner, id: String, secret: String) {
        val record = PinRecord(id = id, fp = store.sealFingerprint(sha256Hex(secret)))
        store.writeSetting(PinRecord.settingKey(owner), PinRecord.encode(record))
    }

    private suspend fun clearPins(owner: Owner) {
        store.writeSetting(PinRecord.settingKey(owner), "")
        if (owner == LegacyBackupEntry.OWNER) store.writeSetting(LegacyBackupEntry.PIN_SETTING, "")
    }

    private sealed interface Resolution {
        data class Found(val entry: VaultEntry) : Resolution
        data object NotFound : Resolution
        data object Conflict : Resolution
        data class Failed(val code: String) : Resolution
    }

    private sealed interface PinRead {
        data class Value(val record: PinRecord?, val legacy: Boolean) : PinRead
        data class Failed(val code: String) : PinRead
    }

    private companion object {
        val CONFLICT = VaultOutcome.NeedsUser(UserAction.CHOOSE_ENTRY, UserReason.CONFLICT)
    }
}
