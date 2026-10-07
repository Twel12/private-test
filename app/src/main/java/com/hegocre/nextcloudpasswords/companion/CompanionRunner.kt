package com.hegocre.nextcloudpasswords.companion

import android.app.PendingIntent
import foundation.e.passwords.companion.AlreadyExists
import foundation.e.passwords.companion.CompanionResult
import foundation.e.passwords.companion.Deleted
import foundation.e.passwords.companion.Failed
import foundation.e.passwords.companion.FailureCode
import foundation.e.passwords.companion.Found
import foundation.e.passwords.companion.NeedsUser
import foundation.e.passwords.companion.NotFound
import foundation.e.passwords.companion.Saved

object CompanionRunner {

    suspend fun run(vault: CompanionVault, request: PendingRequest): VaultOutcome =
        when (val operation = request.operation) {
            PendingOperation.Get -> vault.get(request.owner)
            is PendingOperation.Save -> with(operation.request) {
                vault.save(request.owner, secret, mode, presentation)
            }
            PendingOperation.Delete -> vault.delete(request.owner)
        }

    fun toResult(
        outcome: VaultOutcome,
        needsUser: ((VaultOutcome.NeedsUser) -> PendingIntent)? = null,
    ): CompanionResult = when (outcome) {
        is VaultOutcome.Found -> Found(outcome.secret, outcome.createdAt)
        VaultOutcome.NotFound -> NotFound
        is VaultOutcome.Saved -> Saved(outcome.created)
        VaultOutcome.AlreadyExists -> AlreadyExists
        VaultOutcome.Deleted -> Deleted
        is VaultOutcome.NeedsUser ->
            needsUser?.let { NeedsUser(it(outcome), outcome.action, outcome.reason) } ?: Failed(FailureCode.CANCELED)
        is VaultOutcome.Failed -> Failed(outcome.code)
    }
}
