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
package com.hegocre.nextcloudpasswords.services.autofill

import com.hegocre.nextcloudpasswords.data.password.Password
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnedEntryLookupTest {

    private val owned: (Password) -> Boolean = { it.label == OWNED_LABEL }

    @Test
    fun `no owned entry is absent`() {
        assertEquals(
            OwnedEntryLookup.Absent,
            listOf(entry("1", "other")).toOwnedEntryLookup(owned),
        )
    }

    @Test
    fun `one owned entry is found among others`() {
        val mine = entry("2", OWNED_LABEL)

        assertEquals(
            OwnedEntryLookup.Found(mine),
            listOf(entry("1", "other"), mine).toOwnedEntryLookup(owned),
        )
    }

    @Test
    fun `two owned entries are ambiguous instead of picking one`() {
        assertEquals(
            OwnedEntryLookup.Ambiguous(2),
            listOf(entry("1", OWNED_LABEL), entry("2", OWNED_LABEL)).toOwnedEntryLookup(owned),
        )
    }

    @Test
    fun `nothing found while some entries could not be decrypted is inconclusive`() {
        assertEquals(
            OwnedEntryLookup.Inconclusive,
            listOf(entry("1", "other")).toOwnedEntryLookup(owned, undecryptable = 1),
        )
    }

    @Test
    fun `an owned entry is still found when others could not be decrypted`() {
        val mine = entry("2", OWNED_LABEL)

        assertEquals(
            OwnedEntryLookup.Found(mine),
            listOf(mine).toOwnedEntryLookup(owned, undecryptable = 1),
        )
    }

    private fun entry(id: String, label: String) = Password(
        id = id,
        label = label,
        username = "",
        password = "",
        url = "",
        notes = "",
        customFields = "[]",
        status = 0,
        statusCode = "GOOD",
        hash = "",
        folder = "",
        revision = "",
        share = null,
        shared = false,
        cseType = "none",
        cseKey = "",
        sseType = "",
        client = "",
        hidden = false,
        trashed = false,
        favorite = false,
        editable = true,
        edited = 0,
        created = 0,
        updated = 0,
    )

    private companion object {
        const val OWNED_LABEL = "owned"
    }
}
