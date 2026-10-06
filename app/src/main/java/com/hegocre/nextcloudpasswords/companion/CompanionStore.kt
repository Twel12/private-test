package com.hegocre.nextcloudpasswords.companion

interface CompanionStore {
    suspend fun show(id: String): StoreRead<VaultEntry>

    /** Non-trashed entries that could be decrypted, plus how many could not. */
    suspend fun listAll(): StoreList

    suspend fun create(draft: EntryDraft): Boolean

    suspend fun update(entry: VaultEntry, draft: EntryDraft): Boolean

    suspend fun trash(entry: VaultEntry): Boolean

    suspend fun restore(id: String): Boolean

    suspend fun readSetting(key: String): StoreRead<String?>

    suspend fun writeSetting(key: String, value: String): Boolean

    fun sealFingerprint(fingerprint: String): String?

    fun openFingerprint(sealed: String): String?
}

sealed interface StoreRead<out T> {
    data class Ok<out T>(val value: T) : StoreRead<T>
    data object NotFound : StoreRead<Nothing>
    data object Unreadable : StoreRead<Nothing>
    data class Error(val code: String) : StoreRead<Nothing>
}

sealed interface StoreList {
    data class Ok(val entries: List<VaultEntry>, val undecryptable: Int) : StoreList
    data class Error(val code: String) : StoreList
}
