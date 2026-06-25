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

import foundation.e.autofill.GeneratePasswordResult

internal class ServerPasswordGenerator<T>(
    private val loadClient: suspend () -> T?,
    private val prepareClient: suspend (T) -> Boolean,
    private val isVaultUnlocked: suspend () -> Boolean,
    private val isOnline: suspend () -> Boolean,
    private val requestPassword: suspend (T) -> String?
) {
    suspend fun generate(): GeneratePasswordResult {
        val client = loadClient() ?: return GeneratePasswordResult.VaultLocked
        if (!isVaultUnlocked()) return GeneratePasswordResult.VaultLocked
        if (!isOnline()) return GeneratePasswordResult.Offline
        if (!prepareClient(client)) {
            // Session could not be opened: distinguish a dropped connection from a locked vault.
            return if (isOnline()) GeneratePasswordResult.VaultLocked else GeneratePasswordResult.Offline
        }
        val password = requestPassword(client)
        return when {
            password != null -> GeneratePasswordResult.Success(password)
            !isOnline() -> GeneratePasswordResult.Offline
            else -> GeneratePasswordResult.Failed
        }
    }
}
