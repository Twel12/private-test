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

import com.hegocre.nextcloudpasswords.data.password.Password

internal fun testPassword(
    id: String,
    password: String = "secret-$id",
    username: String = "user",
    label: String = "Label",
    url: String = "",
    notes: String = "",
    customFields: String = "[]",
    edited: Int = 0,
    updated: Int = 0,
    hidden: Boolean = false,
    trashed: Boolean = false,
    editable: Boolean = true,
) = Password(
    id = id,
    label = label,
    username = username,
    password = password,
    url = url,
    notes = notes,
    customFields = customFields,
    status = 0,
    statusCode = "GOOD",
    hash = "",
    folder = "",
    revision = "rev-$id",
    share = null,
    shared = false,
    cseType = "none",
    cseKey = "",
    sseType = "none",
    client = "test",
    hidden = hidden,
    trashed = trashed,
    favorite = false,
    editable = editable,
    edited = edited,
    created = 0,
    updated = updated,
)

internal fun legacyBackupPassword(id: String, edited: Int = 0) = testPassword(
    id = id,
    label = "Murena Device Backups",
    username = "Murena Backups",
    url = "android://foundation.e.backup",
    notes = "BACKUP APP KEY: warning! don't modify manually",
    customFields = """[{"label":"foundation.e.backup.key","type":"data","value":"murena-device-backup:v1"}]""",
    edited = edited,
)
