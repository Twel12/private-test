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

import foundation.e.passwords.companion.AlreadyExists
import foundation.e.passwords.companion.Deleted
import foundation.e.passwords.companion.Failed
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.Found
import foundation.e.passwords.companion.NotFound
import foundation.e.passwords.companion.Saved
import foundation.e.passwords.companion.UserAction
import foundation.e.passwords.companion.UserReason
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionRunnerTest {

    @Test
    fun `vault outcomes map to the library results`() {
        assertEquals(Found("s", 2L), CompanionRunner.toResult(VaultOutcome.Found("s", 2L)))
        assertEquals(NotFound, CompanionRunner.toResult(VaultOutcome.NotFound))
        assertEquals(Saved(true), CompanionRunner.toResult(VaultOutcome.Saved(true)))
        assertEquals(AlreadyExists, CompanionRunner.toResult(VaultOutcome.AlreadyExists))
        assertEquals(Deleted, CompanionRunner.toResult(VaultOutcome.Deleted))
        assertEquals(
            Failed(FailureCode.MODIFIED, false),
            CompanionRunner.toResult(VaultOutcome.Failed(FailureCode.MODIFIED)),
        )
    }

    @Test
    fun `needs user without an intent factory ends as cancelled`() {
        val outcome = VaultOutcome.NeedsUser(UserAction.UNLOCK_ON_DEVICE, UserReason.VAULT_LOCKED)
        assertEquals(Failed(FailureCode.CANCELED, true), CompanionRunner.toResult(outcome))
    }
}
