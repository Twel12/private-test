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

import android.os.Build
import androidx.annotation.RequiresApi
import com.hegocre.nextcloudpasswords.NCPApplication
import foundation.e.autofill.CredentialGetActivity
import foundation.e.autofill.CredentialSaveConfirmationActivity
import foundation.e.autofill.MurenaCredentialProviderService
import foundation.e.autofill.MurenaPasswordBackend
import foundation.e.autofill.R

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class NCPCredentialProviderService : MurenaCredentialProviderService() {

    override fun passwordBackend(): MurenaPasswordBackend {
        return NCPApplication.passwordBackend(this)
    }

    override fun credentialSaveActivityClass(): Class<out CredentialSaveConfirmationActivity> {
        return NCPCredentialSaveConfirmationActivity::class.java
    }

    override fun credentialGetActivityClass(): Class<out CredentialGetActivity> {
        return NCPCredentialGetActivity::class.java
    }

    override fun credentialUnlockActivityClass(): Class<out android.app.Activity> {
        return NCPCredentialUnlockActivity::class.java
    }

    override fun createEntryAccountName(): String {
        return getString(R.string.credential_provider_create_entry_title)
    }

    override fun createEntryDescription(): String {
        return getString(R.string.credential_provider_create_entry_description)
    }

    override fun unlockActionTitle(): String {
        return getString(R.string.autofill_unlock_vault)
    }

    override fun privilegedAppAllowlistJson(): String {
        return NCPCredentialManagerPrivilegedApps.json(this)
    }
}
