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
package foundation.e.autofill

interface MurenaPasswordBackend {
    suspend fun query(request: PasswordQuery): PasswordQueryResult

    suspend fun resolve(request: PasswordResolveRequest): PasswordEntry?

    suspend fun save(request: PasswordSaveRequest): PasswordSaveResult

    suspend fun unlock(request: VaultUnlockRequest): VaultUnlockResult = VaultUnlockResult.Unlocked

    suspend fun report(event: PasswordEvent) = Unit
}

enum class PasswordRequestSource {
    AUTOFILL,
    CREDENTIAL_MANAGER
}

data class PasswordQuery(
    val source: PasswordRequestSource,
    val packageName: String?,
    val webDomain: String?,
    val origin: String?,
    val usernameHint: String?,
    val hasPasswordField: Boolean,
    val isWebOriginRequest: Boolean = false
)

data class PasswordResolveRequest(
    val source: PasswordRequestSource,
    val credentialId: String,
    val packageName: String?,
    val webDomain: String?,
    val origin: String?,
    val isWebOriginRequest: Boolean = false
)

data class PasswordSaveRequest(
    val source: PasswordRequestSource,
    val packageName: String?,
    val webDomain: String?,
    val origin: String?,
    val username: String?,
    val password: String,
    val isWebOriginRequest: Boolean = false
)

data class VaultUnlockRequest(
    val source: PasswordRequestSource,
    val packageName: String?,
    val webDomain: String?,
    val origin: String?,
    val isWebOriginRequest: Boolean = false,
    val secret: String? = null
)

data class PasswordQueryResult(
    val credentials: List<PasswordEntry>,
    val savedPasswordCount: Int = credentials.size,
    val allowSavePrompt: Boolean = true,
    val vaultLocked: Boolean = false
)

data class PasswordEntry(
    val id: String,
    val username: String,
    val password: String?,
    val displayName: String? = null,
    val locked: Boolean = password == null
) {
    val label: String
        get() = displayName ?: username
}

sealed interface PasswordSaveResult {
    data object Saved : PasswordSaveResult
    data object DuplicateIgnored : PasswordSaveResult
    data object NeedsUnlock : PasswordSaveResult
    data class NeedsUserInteraction(val reason: String?) : PasswordSaveResult
    data class QueuedForRetry(val reason: String?) : PasswordSaveResult
    data class Failed(val message: String?) : PasswordSaveResult
}

sealed interface VaultUnlockResult {
    data object Unlocked : VaultUnlockResult
    data object Canceled : VaultUnlockResult
    data class Failed(val message: String?) : VaultUnlockResult
}

sealed interface PasswordEvent {
    data class CredentialSelected(
        val source: PasswordRequestSource,
        val credentialId: String,
        val packageName: String?,
        val webDomain: String?,
        val origin: String?,
        val isWebOriginRequest: Boolean = false
    ) : PasswordEvent
}
