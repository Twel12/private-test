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

    /** Without [needsUser] (the request screen), an outcome still needing the user ends as cancelled. */
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
