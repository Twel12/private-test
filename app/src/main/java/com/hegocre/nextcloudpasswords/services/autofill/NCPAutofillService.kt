package com.hegocre.nextcloudpasswords.services.autofill

import android.content.Intent
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.ui.activities.AutoLoginActivity
import foundation.e.autofill.MurenaAutoFillService
import foundation.e.autofill.MurenaPasswordBackend
import foundation.e.autofill.AutofillDatasetAuthActivity
import foundation.e.autofill.PasswordSaveRequest
import foundation.e.autofill.R

class NCPAutofillService : MurenaAutoFillService() {

    override fun shouldIgnoreFillRequest(packageName: String, webDomain: String?): Boolean {
        return webDomain == null && packageName in IGNORED_FILL_PACKAGES
    }

    override fun passwordBackend(): MurenaPasswordBackend {
        return NCPApplication.passwordBackend(this)
    }

    override fun autofillDatasetAuthActivityClass(): Class<out AutofillDatasetAuthActivity> {
        return NCPAutofillDatasetAuthActivity::class.java
    }

    override fun autofillSelectionIntent(
        packageName: String,
        webDomain: String?,
        usernameHint: String?
    ): Intent {
        val searchHint = webDomain
            ?: packageName.substringAfterLast('.')
        return AutoLoginActivity.autofillSelectionIntent(this, searchHint)
    }

    override fun saveInteractionIntent(request: PasswordSaveRequest): Intent {
        return NCPAutofillSaveInteractionActivity.intent(this, request)
    }

    override fun openAppToFinishSavingMessage(): String {
        return getString(R.string.autofill_open_app_to_finish_saving)
    }

    override fun saveCustomDescriptionText(): String {
        return getString(R.string.autofill_save_description)
    }

    override fun delayedUsernameSaveDescriptionText(): String {
        return getString(R.string.autofill_delayed_username_save_description)
    }

    override fun chooseLoginHeaderText(): String {
        return getString(R.string.autofill_choose_login_header)
    }

    override fun unlockDatasetLabel(): String {
        return getString(R.string.autofill_unlock_vault)
    }

    companion object {
        const val AUTOFILL_REQUEST = "autofill_request"
        const val AUTOFILL_SEARCH_HINT = "autofill_query"

        private val IGNORED_FILL_PACKAGES = setOf(
            "com.android.settings",
            "foundation.e.parentalcontrol",
        )
    }

}
