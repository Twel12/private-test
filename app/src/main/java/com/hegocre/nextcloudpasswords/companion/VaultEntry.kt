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

data class VaultEntry(
    val id: String,
    val revision: String,
    val label: String,
    val username: String,
    val secret: String,
    val url: String,
    val notes: String,
    val customFields: List<CustomField>,
    val folder: String,
    val favorite: Boolean,
    val hidden: Boolean,
    val trashed: Boolean,
    val edited: Int,
    val created: Long,
) {
    override fun toString() = "VaultEntry(id=$id, trashed=$trashed)"
}

data class EntryDraft(
    val label: String,
    val username: String,
    val secret: String,
    val url: String,
    val notes: String,
    val customFields: List<CustomField>,
) {
    override fun toString() = "EntryDraft(label=$label)"
}
