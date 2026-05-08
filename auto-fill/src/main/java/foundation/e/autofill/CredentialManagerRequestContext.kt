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

import androidx.core.net.toUri
import androidx.credentials.provider.CallingAppInfo

data class CredentialManagerRequestContext(
    val packageName: String?,
    val webDomain: String?,
    val origin: String?,
    val isWebOriginRequest: Boolean
) {
    val affiliatedDomain: String?
        get() = webDomain ?: packageName
}

fun CallingAppInfo?.toCredentialManagerRequestContext(
    privilegedAppAllowlistJson: String?
): CredentialManagerRequestContext {
    val callingAppInfo = this
        ?: return CredentialManagerRequestContext(
            packageName = null,
            webDomain = null,
            origin = null,
            isWebOriginRequest = false
        )

    val hasOrigin = callingAppInfo.isOriginPopulated()
    val origin = privilegedAppAllowlistJson
        ?.takeIf { it.isNotBlank() && hasOrigin }
        ?.let { allowlist ->
            runCatching { callingAppInfo.getOrigin(allowlist) }.getOrNull()
        }
        ?.takeIf { it.isNotBlank() }

    return CredentialManagerRequestContext(
        packageName = callingAppInfo.packageName,
        webDomain = origin?.hostOrNull(),
        origin = origin,
        isWebOriginRequest = hasOrigin
    )
}

private fun String.hostOrNull(): String? {
    return runCatching { toUri().host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}
