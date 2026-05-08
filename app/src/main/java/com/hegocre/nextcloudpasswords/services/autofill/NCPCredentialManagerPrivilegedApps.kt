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

import android.content.Context
import com.hegocre.nextcloudpasswords.R

object NCPCredentialManagerPrivilegedApps {
    @Volatile
    private var cachedJson: String? = null

    fun json(context: Context): String {
        cachedJson?.let { return it }

        return synchronized(this) {
            cachedJson ?: context.resources
                .openRawResource(R.raw.credential_manager_privileged_apps)
                .bufferedReader()
                .use { it.readText() }
                .also { cachedJson = it }
        }
    }
}
