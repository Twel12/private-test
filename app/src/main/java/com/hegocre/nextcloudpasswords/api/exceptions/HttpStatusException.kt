/*
 * Copyright (C) 2026 e Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hegocre.nextcloudpasswords.api.exceptions

import com.hegocre.nextcloudpasswords.utils.Error
import java.io.IOException
import java.net.HttpURLConnection

class HttpStatusException(
    val statusCode: Int,
    cause: Throwable? = null
) : IOException("HTTP request failed with status $statusCode", cause)

fun Throwable.httpStatusCodeOrNull(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpStatusException) return current.statusCode
        current = current.cause
    }
    return null
}

fun Throwable.twoFactorErrorCodeOrNull(): Int? =
    if (httpStatusCodeOrNull() == HttpURLConnection.HTTP_SEE_OTHER)
        Error.TWO_FACTOR_APP_PASSWORD_REQUIRED
    else
        null
