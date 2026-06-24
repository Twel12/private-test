package com.hegocre.nextcloudpasswords.services.autofill

import android.content.Intent
import android.view.autofill.AutofillId
import com.hegocre.nextcloudpasswords.NCPApplication
import com.hegocre.nextcloudpasswords.ui.activities.AutoLoginActivity
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import foundation.e.autofill.AutofillDatasetAuthActivity
import foundation.e.autofill.MurenaAutoFillService
import foundation.e.autofill.MurenaPasswordBackend
import foundation.e.autofill.PasswordSaveRequest

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
        return AutoLoginActivity.autofillSelectionIntent(this, searchHint, webDomain)
    }

    override fun saveInteractionIntent(request: PasswordSaveRequest): Intent {
        return NCPAutofillSaveInteractionActivity.intent(this, request)
    }

    override fun isInlineAutofillEnabled(): Boolean {
        return PreferencesManager.getInstance(this).getUseInlineAutofill()
    }

    override fun suggestPasswordIntent(
        passwordIds: List<AutofillId>
    ): Intent {
        return NCPSuggestPasswordActivity.autofillIntent(
            context = this,
            passwordIds = passwordIds
        )
    }

    companion object {
        const val AUTOFILL_REQUEST = "autofill_request"
        const val AUTOFILL_SEARCH_HINT = "autofill_query"
    }

}
