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

import android.app.Activity
import android.content.Intent
import java.util.UUID

object NCPAutofillPendingSaveContinuation {
    const val EXTRA_TOKEN = "foundation.e.passwords.autofill.SAVE_CONTINUATION_TOKEN"

    private var token: String? = null
    private var pendingSave: NCPAutofillPendingSaveStore.PendingSave? = null

    @Synchronized
    fun isActive(): Boolean = token != null

    @Synchronized
    fun begin(save: NCPAutofillPendingSaveStore.PendingSave): String {
        val newToken = UUID.randomUUID().toString()
        token = newToken
        pendingSave = save
        return newToken
    }

    @Synchronized
    fun matches(token: String?): Boolean = token != null && token == this.token

    @Synchronized
    fun get(token: String?): NCPAutofillPendingSaveStore.PendingSave? =
        if (matches(token)) pendingSave else null

    @Synchronized
    fun clear() {
        token = null
        pendingSave = null
    }

    @Synchronized
    fun clearIfMatches(token: String?) {
        if (matches(token)) {
            clear()
        }
    }
}

/** Relaunch the unlock activity to finish the pending save once logged in. */
fun Activity.resumeAutofillSave(token: String) {
    startActivity(
        Intent(this, NCPAutofillPendingSaveUnlockActivity::class.java)
            .putExtra(NCPAutofillPendingSaveContinuation.EXTRA_TOKEN, token)
    )
    finish()
}

/** Drop the pending save if this login screen is being dismissed without continuing the flow. */
fun Activity.clearAutofillSaveIfAbandoned(token: String?, continuingFlow: Boolean) {
    val abandoned = isFinishing && !isChangingConfigurations && !continuingFlow
    if (abandoned) {
        NCPAutofillPendingSaveContinuation.clearIfMatches(token)
    }
}
