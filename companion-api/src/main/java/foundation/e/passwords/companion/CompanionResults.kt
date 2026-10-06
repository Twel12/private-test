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
package foundation.e.passwords.companion

import android.app.PendingIntent

sealed interface CompanionResult
sealed interface GetResult : CompanionResult
sealed interface SaveResult : CompanionResult
sealed interface DeleteResult : CompanionResult
sealed interface CommonResult : GetResult, SaveResult, DeleteResult

data class Found(val secret: String, val createdAt: Long) : GetResult {
    override fun toString() = "Found(createdAt=$createdAt)"
}

data object NotFound : GetResult, DeleteResult

data class Saved(val created: Boolean) : SaveResult

data object AlreadyExists : SaveResult

data object Deleted : DeleteResult

data class NeedsUser(val intent: PendingIntent, val action: String, val reason: String) : CommonResult

data class Failed(
    val code: String,
    val retryable: Boolean = FailureCode.isRetryable(code),
) : CommonResult
