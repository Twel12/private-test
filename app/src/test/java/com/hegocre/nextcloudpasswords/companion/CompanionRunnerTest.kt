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
