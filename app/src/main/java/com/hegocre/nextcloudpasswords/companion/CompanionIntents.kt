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
import android.content.Context
import android.content.Intent
import android.net.Uri

object CompanionIntents {
    const val EXTRA_TOKEN = "foundation.e.passwords.companion.extra.TOKEN"

    fun request(context: Context, token: String): PendingIntent {
        val intent = Intent(context, CompanionRequestActivity::class.java)
            .setData(Uri.fromParts("companion", token, null))
            .putExtra(EXTRA_TOKEN, token)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
    }
}
