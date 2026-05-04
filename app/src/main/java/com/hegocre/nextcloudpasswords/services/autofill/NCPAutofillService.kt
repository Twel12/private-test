package com.hegocre.nextcloudpasswords.services.autofill

import android.content.Intent
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.ui.activities.MainActivity
import foundation.e.auto_fill.MurenaAutoFillService
import foundation.e.auto_fill.MurenaPasswordBackend
import foundation.e.auto_fill.AutofillDatasetAuthActivity
import foundation.e.auto_fill.PasswordSaveRequest
import foundation.e.auto_fill.R

class NCPAutofillService : MurenaAutoFillService() {

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
        return Intent(this, MainActivity::class.java)
            .putExtra(AUTOFILL_REQUEST, true)
            .putExtra(AUTOFILL_SEARCH_HINT, searchHint)
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
    }

}
