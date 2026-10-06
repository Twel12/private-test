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
import foundation.e.passwords.companion.EntryPresentation

internal val BACKUP = Owner("foundation.e.backup", "murena-device-backup:v1")
internal val FMD = Owner("foundation.e.findmydevice", "0f3a9c")

internal fun EntryDraft.toEntry(id: String, created: Long = 1L) = VaultEntry(
    id = id, revision = "rev-$id", label = label, username = username, secret = secret, url = url,
    notes = notes, customFields = customFields, folder = "folder", favorite = false, hidden = false,
    trashed = false, edited = 0, created = created,
)

internal fun ownedEntry(id: String, owner: Owner, secret: String = "secret-$id") =
    OwnedEntry.draft(owner, secret, EntryPresentation("L", "U", "N")).toEntry(id)

internal fun legacyBackupEntry(id: String, secret: String = "legacy-key") = VaultEntry(
    id = id, revision = "rev-$id", label = "Murena Device Backups", username = "Murena Backups",
    secret = secret, url = "android://foundation.e.backup",
    notes = "BACKUP APP KEY: warning! don't modify manually",
    customFields = listOf(CustomField("foundation.e.backup.key", CustomField.TYPE_DATA, "murena-device-backup:v1")),
    folder = "", favorite = false, hidden = false, trashed = false, edited = 0, created = 1L,
)

internal fun plainEntry(id: String) = VaultEntry(
    id = id, revision = "rev-$id", label = "Bank", username = "me", secret = "pw", url = "https://bank.example",
    notes = "", customFields = emptyList(), folder = "", favorite = false, hidden = false, trashed = false,
    edited = 0, created = 1L,
)
