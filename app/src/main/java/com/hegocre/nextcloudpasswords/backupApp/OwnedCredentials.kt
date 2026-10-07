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

import android.app.PendingIntent
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.services.autofill.NCPAutofillMetadata
import foundation.e.backupappapi.CredentialResult
import foundation.e.autofill.PasswordSaveResult
import foundation.e.backupappapi.PasswordsApp

sealed interface OwnedCredentialResult {
    data class Found(val secret: String) : OwnedCredentialResult {
        override fun toString() = "Found(secret=<redacted>)"
    }

    data object NotFound : OwnedCredentialResult
    data object Locked : OwnedCredentialResult
    data class Failed(val retryable: Boolean) : OwnedCredentialResult
}

internal sealed interface OwnedLookup {
    data class Hit(val password: Password) : OwnedLookup
    data object Miss : OwnedLookup
    data object SyncFailed : OwnedLookup
}

internal fun selectOwnedEntry(passwords: List<Password>, packageName: String, key: String): Password? {
    val visible = passwords.filter { !it.trashed && !it.hidden }
    val owned = visible.filter {
        NCPAutofillMetadata.identityKey(it.customFields) == key.trim() &&
            packageName in NCPAutofillMetadata.packageNames(it.customFields)
    }
    val candidates = owned.ifEmpty {
        if (packageName == PasswordsApp.BACKUP_APP_PACKAGE && key == PasswordsApp.BACKUP_KEY) {
            visible.filter(BackupAppPassword::matches)
        } else {
            emptyList()
        }
    }
    return candidates.maxWithOrNull(compareBy<Password>({ it.edited }, { it.updated }))
}

internal suspend fun lookupWithSync(
    lookup: suspend () -> Password?,
    sync: suspend () -> Boolean,
): OwnedLookup {
    lookup()?.let { return OwnedLookup.Hit(it) }
    return if (sync()) lookup()?.let(OwnedLookup::Hit) ?: OwnedLookup.Miss else OwnedLookup.SyncFailed
}

internal fun OwnedCredentialResult.toCredentialResult(
    unlockIntent: () -> PendingIntent
): CredentialResult = when (this) {
    is OwnedCredentialResult.Found -> CredentialResult.ok(secret)
    OwnedCredentialResult.NotFound -> CredentialResult.notFound()
    OwnedCredentialResult.Locked -> CredentialResult.needsUser(unlockIntent())
    is OwnedCredentialResult.Failed -> CredentialResult.error(retryable)
}

internal sealed interface OwnedSave {
    data object Create : OwnedSave
    data class ReturnStored(val secret: String) : OwnedSave {
        override fun toString() = "ReturnStored(secret=<redacted>)"
    }

    data object Update : OwnedSave
}

internal fun ownedSaveDecision(
    existing: Password?,
    secret: String,
    username: String,
    replace: Boolean,
    readOnly: Boolean,
): OwnedSave {
    if (existing == null) return OwnedSave.Create
    val unchanged = existing.password == secret &&
        existing.username == username &&
        NCPAutofillMetadata.isReadOnly(existing.customFields) == readOnly
    return if (!replace || unchanged) OwnedSave.ReturnStored(existing.password) else OwnedSave.Update
}

internal fun PasswordSaveResult.toOwnedResult(secret: String): OwnedCredentialResult = when (this) {
    PasswordSaveResult.Saved,
    PasswordSaveResult.DuplicateIgnored -> OwnedCredentialResult.Found(secret)
    PasswordSaveResult.NeedsUnlock -> OwnedCredentialResult.Locked
    else -> OwnedCredentialResult.Failed(retryable = true)
}

internal fun ownedCreateAllowed(encryptionCse: Int?): Boolean = encryptionCse != 0
