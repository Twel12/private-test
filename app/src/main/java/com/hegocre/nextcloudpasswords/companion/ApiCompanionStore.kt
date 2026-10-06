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

import android.content.Context
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.FoldersApi
import com.hegocre.nextcloudpasswords.api.encryption.CSEv1Keychain
import com.hegocre.nextcloudpasswords.data.password.DeletedPassword
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.Result
import com.hegocre.nextcloudpasswords.utils.encryptValue
import com.hegocre.nextcloudpasswords.utils.sha1Hash
import foundation.e.passwords.companion.FailureCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class ApiCompanionStore(context: Context) : CompanionStore {

    private val appContext = context.applicationContext
    private val api: ApiController get() = ApiController.getInstance(appContext)

    override suspend fun show(id: String): StoreRead<VaultEntry> = when (val result = api.showPassword(id)) {
        is Result.Success -> result.data.toVaultEntry()?.let { StoreRead.Ok(it) } ?: StoreRead.Unreadable
        is Result.Error ->
            if (result.code == Error.API_NOT_FOUND) StoreRead.NotFound else StoreRead.Error(result.code.toFailureCode())
    }

    override suspend fun listAll(): StoreList = when (val result = api.listPasswords()) {
        is Result.Success -> {
            val decoded = result.data.filterNot { it.trashed }.map { it.toVaultEntry() }
            StoreList.Ok(decoded.filterNotNull(), decoded.count { it == null })
        }
        is Result.Error -> StoreList.Error(result.code.toFailureCode())
    }

    override suspend fun create(draft: EntryDraft): Boolean {
        val keychain = encryptionKeychain() ?: return false
        return api.createPassword(draft.toNewPassword(keychain))
    }

    override suspend fun update(entry: VaultEntry, draft: EntryDraft): Boolean {
        val keychain = encryptionKeychain() ?: return false
        return api.updatePassword(draft.toUpdatedPassword(entry, keychain))
    }

    override suspend fun trash(entry: VaultEntry): Boolean =
        api.deletePassword(DeletedPassword(entry.id, entry.revision))

    override suspend fun restore(id: String): Boolean = api.restorePassword(id)

    override suspend fun readSetting(key: String): StoreRead<String?> = when (val result = api.getUserSetting(key)) {
        is Result.Success -> StoreRead.Ok(result.data)
        is Result.Error -> StoreRead.Error(result.code.toFailureCode())
    }

    override suspend fun writeSetting(key: String, value: String): Boolean =
        api.setUserSetting(key, value) is Result.Success

    override fun fingerprint(secret: String): String? {
        val keychain = encryptionKeychain() ?: return null
        return PinFingerprint.of(keychain.current, keychain.keys.getValue(keychain.current), secret)
    }

    override fun fingerprintMatches(stored: String, secret: String): Boolean? {
        val keychain = api.currentKeychain() ?: return null
        return PinFingerprint.matches(stored, secret, keychain.keys)
    }

    // encryptValue() returns plain text for a blank key, so never write with one.
    private fun encryptionKeychain(): CSEv1Keychain? = usableKeychain(api.currentKeychain())

    private suspend fun Password.toVaultEntry(): VaultEntry? {
        val decrypted = runCatching { decrypt(api.currentKeychain()) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull() ?: return null
        return VaultEntry(
            id = decrypted.id,
            revision = decrypted.revision,
            label = decrypted.label,
            username = decrypted.username,
            secret = decrypted.password,
            url = decrypted.url,
            notes = decrypted.notes,
            customFields = OwnedEntry.parseFields(decrypted.customFields),
            folder = decrypted.folder,
            favorite = decrypted.favorite,
            hidden = decrypted.hidden,
            trashed = decrypted.trashed,
            edited = decrypted.edited,
            created = decrypted.created.toLong(),
        )
    }

    private fun EntryDraft.toNewPassword(keychain: CSEv1Keychain) = NewPassword(
        password = secret.encryptValue(keychain.current, keychain),
        label = label.encryptValue(keychain.current, keychain),
        username = username.encryptValue(keychain.current, keychain),
        url = url.encryptValue(keychain.current, keychain),
        notes = notes.encryptValue(keychain.current, keychain),
        customFields = Json.encodeToString(customFields).encryptValue(keychain.current, keychain),
        hash = secret.sha1Hash().take(hashLength()),
        cseType = ApiController.CSE_TYPE,
        cseKey = keychain.current,
        folder = FoldersApi.DEFAULT_FOLDER_UUID,
        edited = 0,
        hidden = false,
        favorite = false,
    )

    private fun EntryDraft.toUpdatedPassword(entry: VaultEntry, keychain: CSEv1Keychain) = UpdatedPassword(
        id = entry.id,
        revision = entry.revision,
        password = secret.encryptValue(keychain.current, keychain),
        label = label.encryptValue(keychain.current, keychain),
        username = username.encryptValue(keychain.current, keychain),
        url = url.encryptValue(keychain.current, keychain),
        notes = notes.encryptValue(keychain.current, keychain),
        customFields = Json.encodeToString(customFields).encryptValue(keychain.current, keychain),
        hash = secret.sha1Hash().take(hashLength()),
        cseType = ApiController.CSE_TYPE,
        cseKey = keychain.current,
        folder = entry.folder,
        edited = if (secret == entry.secret) entry.edited else 0,
        hidden = entry.hidden,
        favorite = entry.favorite,
    )

    private fun hashLength(): Int = api.serverSettings.value?.passwordSecurityHash ?: DEFAULT_HASH_LENGTH

    private fun Int.toFailureCode(): String = when (this) {
        Error.API_TIMEOUT, Error.UNKNOWN, Error.API_NO_SESSION -> FailureCode.NETWORK
        else -> FailureCode.SERVER
    }

    internal companion object {
        private const val DEFAULT_HASH_LENGTH = 40

        fun usableKeychain(keychain: CSEv1Keychain?): CSEv1Keychain? =
            keychain?.takeIf { it.current.isNotBlank() && it.keys.containsKey(it.current) }
    }
}
