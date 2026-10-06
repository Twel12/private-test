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
