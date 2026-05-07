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
package foundation.e.auto_fill

import android.app.Activity
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.CustomDescription
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.service.autofill.RegexValidator
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.service.autofill.SavedDatasetsInfo
import android.service.autofill.SavedDatasetsInfoCallback
import android.service.autofill.TextValueSanitizer
import android.service.autofill.UserData
import android.service.autofill.Validators
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.EditorInfo
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.os.BundleCompat
import timber.log.Timber
import java.util.regex.Pattern

abstract class MurenaAutoFillService : AutofillService() {
    protected abstract fun passwordBackend(): MurenaPasswordBackend

    protected abstract fun autofillDatasetAuthActivityClass(): Class<out Activity>

    protected open fun autofillSelectionIntent(
        packageName: String,
        webDomain: String?,
        usernameHint: String?
    ): Intent? = null

    protected open fun saveInteractionIntent(request: PasswordSaveRequest): Intent? = null

    protected open fun openAppToFinishSavingMessage(): String =
        getString(R.string.autofill_open_app_to_finish_saving)

    protected open fun saveCustomDescriptionText(): String =
        getString(R.string.autofill_save_description)

    protected open fun delayedUsernameSaveDescriptionText(): String =
        getString(R.string.autofill_delayed_username_save_description)

    protected open fun chooseLoginHeaderText(): String =
        getString(R.string.autofill_choose_login_header)

    protected open fun unlockDatasetLabel(): String =
        getString(R.string.autofill_unlock_vault)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        fillCallback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            fillCallback.onSuccess(null)
            return
        }

        val loginFields = LoginFieldParser(structure).parse()
        Timber.d(
            "onFillRequest parsed package=${loginFields.packageName}, " +
                "webDomain=${loginFields.webDomain}, usernameIds=${loginFields.usernameIds.size}, " +
                "passwordIds=${loginFields.passwordIds.size}, ignoredIds=${loginFields.ignoredIds.size}, " +
                "hasTrigger=${loginFields.triggerId != null}"
        )
        if (loginFields.packageName == packageName) {
            Timber.d("Ignoring own package=${loginFields.packageName}")
            fillCallback.onSuccess(
                FillResponse.Builder()
                    .disableAutofill(DISABLE_AUTOFILL_DURATION_MILLIS)
                    .build()
            )
            return
        }

        if (loginFields.usernameIds.isEmpty() && loginFields.passwordIds.isEmpty()) {
            Timber.d("No username/password AutofillIds found; returning null response")
            fillCallback.onSuccess(null)
            return
        }

        val query = loginFields.toPasswordQuery()
        Timber.d("Querying backend: $query")
        runBackendCall(
            cancellationSignal = cancellationSignal,
            call = {
                passwordBackend().query(query)
            },
            onSuccess = { queryResult ->
                Timber.d(
                    "Backend result credentials=${queryResult.credentials.size}, " +
                        "savedPasswordCount=${queryResult.savedPasswordCount}, " +
                        "allowSavePrompt=${queryResult.allowSavePrompt}, " +
                        "ids=${queryResult.credentials.joinToString { it.id }}"
                )
                fillCallback.onSuccess(
                    buildFillResponse(
                        request = request,
                        loginFields = loginFields,
                        queryResult = queryResult
                    )
                )
            },
            onError = { error ->
                Timber.e(error,"Failed to query password backend" )
                fillCallback.onSuccess(null)
            }
        )
    }

    override fun onSaveRequest(
        saveRequest: SaveRequest,
        saveCallback: SaveCallback
    ) {
        val structure = saveRequest.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            saveCallback.onFailure("No autofill structure found")
            return
        }

        val loginFields = LoginFieldParser(structure).parse()
        val usernameIds = saveRequest.clientState?.let { clientState ->
            BundleCompat.getParcelableArrayList(
                clientState,
                CLIENT_STATE_USERNAME_IDS,
                AutofillId::class.java
            )
        }.orEmpty()
            .ifEmpty { loginFields.usernameIds }
        val passwordIds = saveRequest.clientState?.let { clientState ->
            BundleCompat.getParcelableArrayList(
                clientState,
                CLIENT_STATE_PASSWORD_IDS,
                AutofillId::class.java
            )
        }.orEmpty()
            .ifEmpty { loginFields.passwordIds }
        val username = saveRequest.fillContexts.findLatestTextValue(usernameIds).orEmpty()
        val password = saveRequest.fillContexts.findLatestTextValue(passwordIds).orEmpty()

        Timber.d(
            "onSaveRequest parsed package=${loginFields.packageName}, webDomain=${loginFields.webDomain}, " +
                "usernameIds=${usernameIds.size}, passwordIds=${passwordIds.size}, " +
                "usernamePresent=${username.isNotBlank()}, passwordPresent=${password.isNotBlank()}"
        )

        if (password.isBlank()) {
            Timber.d("onSaveRequest failed: no password entered")
            saveCallback.onFailure("No password entered")
            return
        }

        val target = saveRequest.clientState?.getString(CLIENT_STATE_TARGET)
            ?: loginFields.webDomain
            ?: loginFields.packageName

        val passwordSaveRequest = PasswordSaveRequest(
            source = PasswordRequestSource.AUTOFILL,
            packageName = loginFields.packageName,
            webDomain = loginFields.webDomain,
            origin = null,
            username = username.ifBlank { null },
            password = password
        )

        runBackendCall(
            call = {
                passwordBackend().save(passwordSaveRequest)
            },
            onSuccess = { result ->
                Timber.d("Backend save result=$result")
                when (result) {
                    PasswordSaveResult.Saved,
                    PasswordSaveResult.DuplicateIgnored,
                    is PasswordSaveResult.QueuedForRetry -> saveCallback.onSuccess()

                    PasswordSaveResult.NeedsUnlock -> {
                        openSaveInteraction(passwordSaveRequest)
                        saveCallback.onFailure(openAppToFinishSavingMessage())
                    }

                    is PasswordSaveResult.NeedsUserInteraction -> {
                        openSaveInteraction(passwordSaveRequest)
                        saveCallback.onFailure(
                            result.reason ?: openAppToFinishSavingMessage()
                        )
                    }

                    is PasswordSaveResult.Failed -> saveCallback.onFailure(
                        result.message ?: "Could not save password"
                    )
                }
            },
            onError = { error ->
                Timber.e( error, "Failed to save password")
                saveCallback.onFailure(error.message ?: "Could not save password")
            }
        )
    }

    private fun openSaveInteraction(request: PasswordSaveRequest) {
        val intent = saveInteractionIntent(request) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure { error ->
                Timber.w(error,"Failed to open save interaction" )
            }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onSavedDatasetsInfoRequest(callback: SavedDatasetsInfoCallback) {
        runBackendCall(
            call = {
                passwordBackend().query(
                    PasswordQuery(
                        source = PasswordRequestSource.AUTOFILL,
                        packageName = null,
                        webDomain = null,
                        origin = null,
                        usernameHint = null,
                        hasPasswordField = true
                    )
                )
            },
            onSuccess = { queryResult ->
                callback.onSuccess(
                    setOf(
                        SavedDatasetsInfo(
                            SavedDatasetsInfo.TYPE_PASSWORDS,
                            queryResult.savedPasswordCount
                        )
                    )
                )
            },
            onError = { error ->
                Timber.e(error,"Failed to load saved datasets info")
                callback.onSuccess(emptySet())
            }
        )
    }

    private fun buildSaveInfo(loginFields: LoginFields): SaveInfo {
        val requiredIds = loginFields.passwordIds.distinct().toTypedArray()
        val optionalIds = loginFields.usernameIds.distinct().toTypedArray()

        return SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_PASSWORD,
            requiredIds
        )
            .setCustomDescription(
                CustomDescription.Builder(
                    simplePresentation(saveCustomDescriptionText(), packageName)
                ).build()
            )
            .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            .setValidator(buildSaveValidator(loginFields))
            .applySanitizers(loginFields)
            .apply {
                if (optionalIds.isNotEmpty()) {
                    setOptionalIds(optionalIds)
                }
                loginFields.triggerId?.let(::setTriggerId)
                setPositiveAction(SaveInfo.POSITIVE_BUTTON_STYLE_CONTINUE)
            }
            .build()
    }

    private fun buildDelayedUsernameSaveInfo(loginFields: LoginFields): SaveInfo {
        return SaveInfo.Builder(
            SaveInfo.SAVE_DATA_TYPE_USERNAME,
            loginFields.usernameIds.distinct().toTypedArray()
        )
            .setDescription(delayedUsernameSaveDescriptionText())
            .setFlags(SaveInfo.FLAG_DELAY_SAVE)
            .applySanitizers(loginFields)
            .build()
    }

    private fun buildSaveValidator(loginFields: LoginFields) = Validators.and(
        *(loginFields.passwordIds.map { passwordId ->
            RegexValidator(passwordId, NON_EMPTY_TEXT_PATTERN)
        }).toTypedArray()
    )

    private fun buildFillResponse(
        request: FillRequest,
        loginFields: LoginFields,
        queryResult: PasswordQueryResult
    ): FillResponse? {
        val canFillCredential = loginFields.usernameIds.isNotEmpty() ||
            loginFields.passwordIds.isNotEmpty()
        val selectionIntent = if (canFillCredential && !queryResult.vaultLocked) {
            autofillSelectionIntent(
                packageName = loginFields.packageName,
                webDomain = loginFields.webDomain,
                usernameHint = queryResult.credentials.firstOrNull()?.username
            )
        } else {
            null
        }
        val hasCredentialDatasets = canFillCredential &&
            queryResult.credentials.isNotEmpty()
        val hasUnlockAuthentication = canFillCredential && queryResult.vaultLocked
        val hasSelectionDataset = selectionIntent != null
        val hasSaveInfo = queryResult.allowSavePrompt &&
            (loginFields.passwordIds.isNotEmpty() || loginFields.usernameIds.isNotEmpty())

        if (!hasCredentialDatasets && !hasUnlockAuthentication && !hasSelectionDataset && !hasSaveInfo) {
            Timber.d("No datasets and no SaveInfo; returning null FillResponse")
            return null
        }

        Timber.d(
            "Building FillResponse hasCredentialDatasets=$hasCredentialDatasets, " +
                "hasUnlockAuthentication=$hasUnlockAuthentication, " +
                "hasSelectionDataset=$hasSelectionDataset, " +
                "hasSaveInfo=$hasSaveInfo, usernameOnly=${loginFields.passwordIds.isEmpty()}"
        )

        val responseBuilder = FillResponse.Builder()
            .setClientState(buildClientState(loginFields))
            .applyResponseMetadata(loginFields, queryResult)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setIconResourceId(R.drawable.ic_autofill_provider)
                        .setShowFillDialogIcon(true)
                        .setShowSaveDialogIcon(true)
                }
            }

        if (hasCredentialDatasets) {
            responseBuilder.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setDialogHeader(simplePresentation(chooseLoginHeaderText(), packageName))
                }
            }
        }

        if (hasUnlockAuthentication) {
            Timber.d("Adding unlock vault response authentication")
            responseBuilder.applyUnlockVaultAuthentication(loginFields)
        }

        selectionIntent?.let { intent ->
            Timber.d("Adding open-app selection dataset")
            responseBuilder.addDataset(
                buildAutofillSelectionDataset(loginFields, intent)
            )
        }

        if (loginFields.passwordIds.isNotEmpty()) {
            val inlinePresentationSpec =
                request.inlineSuggestionsRequest?.inlinePresentationSpecs?.firstOrNull()

            queryResult.credentials.forEach { credential ->
                if (credential.password.isNullOrBlank() || credential.locked) {
                    Timber.d("Adding locked/auth dataset id=${credential.id}, username=${credential.username}")
                    responseBuilder.addDataset(
                        buildAuthenticatedCredentialDataset(loginFields, credential)
                    )
                } else {
                    Timber.d("Adding password dataset id=${credential.id}, username=${credential.username}")
                    responseBuilder.addDataset(
                        buildCredentialDataset(
                            context = this,
                            authActivityClass = autofillDatasetAuthActivityClass(),
                            credential = credential,
                            usernameIds = loginFields.usernameIds,
                            passwordIds = loginFields.passwordIds,
                            inlinePresentationSpec = inlinePresentationSpec
                        )
                    )
                }
            }

            if (queryResult.allowSavePrompt) {
                responseBuilder.setSaveInfo(buildSaveInfo(loginFields))
            }
        } else {
            queryResult.credentials.filter { it.username.isNotBlank() }.forEach { credential ->
                Timber.d("Adding email-only dataset id=${credential.id}, username=${credential.username}")
                responseBuilder.addDataset(
                    buildCredentialDataset(
                        context = this,
                        authActivityClass = autofillDatasetAuthActivityClass(),
                        credential = credential,
                        usernameIds = loginFields.usernameIds,
                        passwordIds = emptyList()
                    )
                )
            }

            if (queryResult.allowSavePrompt) {
                responseBuilder.setSaveInfo(buildDelayedUsernameSaveInfo(loginFields))
            }
        }

        return responseBuilder.build()
    }

    private fun SaveInfo.Builder.applySanitizers(loginFields: LoginFields): SaveInfo.Builder {
        val ids = (loginFields.usernameIds + loginFields.passwordIds).distinct().toTypedArray()
        if (ids.isNotEmpty()) {
            addSanitizer(TextValueSanitizer(TRIM_TEXT_PATTERN, "$1"), *ids)
        }
        return this
    }

    private fun FillResponse.Builder.applyResponseMetadata(
        loginFields: LoginFields,
        queryResult: PasswordQueryResult
    ): FillResponse.Builder {
        val fillDialogTriggerIds = (loginFields.usernameIds + loginFields.passwordIds)
            .distinct()
            .toTypedArray()
        if (fillDialogTriggerIds.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setFillDialogTriggerIds(*fillDialogTriggerIds)
            }
        }

        val fieldClassificationIds = loginFields.usernameIds.distinct().toTypedArray()
        if (fieldClassificationIds.isNotEmpty()) {
            setFieldClassificationIds(*fieldClassificationIds)
            queryResult.credentials.firstOrNull { it.username.isNotBlank() }
                ?.let { credential ->
                    setUserData(buildUserData(credential))
                }
        }
        if (loginFields.ignoredIds.isNotEmpty()) {
            setIgnoredIds(*loginFields.ignoredIds.distinct().toTypedArray())
        }
        return this
    }

    private fun buildAuthenticatedCredentialDataset(
        loginFields: LoginFields,
        credential: PasswordEntry
    ): Dataset {
        val authIntent = Intent(this, autofillDatasetAuthActivityClass()).apply {
            setIdentifier(credential.id)
            putExtra(AutofillDatasetAuthActivity.EXTRA_CREDENTIAL_ID, credential.id)
            putExtra(AutofillDatasetAuthActivity.EXTRA_PACKAGE_NAME, loginFields.packageName)
            putExtra(AutofillDatasetAuthActivity.EXTRA_WEB_DOMAIN, loginFields.webDomain)
            putParcelableArrayListExtra(
                AutofillDatasetAuthActivity.EXTRA_USERNAME_IDS,
                ArrayList(loginFields.usernameIds)
            )
            putParcelableArrayListExtra(
                AutofillDatasetAuthActivity.EXTRA_PASSWORD_IDS,
                ArrayList(loginFields.passwordIds)
            )
        }

        return datasetBuilder(credential.label, packageName)
            .setAuthentication(
                PendingIntent.getActivity(
                    this,
                    AUTH_DATASET_REQUEST_CODE,
                    authIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ).intentSender
            )
            .apply {
                loginFields.usernameIds.forEach { autofillId ->
                    @Suppress("DEPRECATION")
                    setValue(autofillId, null, simplePresentation(credential.label, packageName))
                }
                loginFields.passwordIds.forEach { autofillId ->
                    @Suppress("DEPRECATION")
                    setValue(autofillId, null, simplePresentation(credential.label, packageName))
                }
            }
            .setId("murena-auth-${credential.id}")
            .build()
    }

    private fun buildAutofillSelectionDataset(
        loginFields: LoginFields,
        selectionIntent: Intent
    ): Dataset {
        val label = getString(R.string.autofill_open_app_selection)
        return datasetBuilder(label, packageName)
            .setAuthentication(
                PendingIntent.getActivity(
                    this,
                    APP_SELECTION_REQUEST_CODE,
                    selectionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingIntentFlag()
                ).intentSender
            )
            .apply {
                loginFields.usernameIds.forEach { autofillId ->
                    @Suppress("DEPRECATION")
                    setValue(autofillId, null, simplePresentation(label, packageName))
                }
                loginFields.passwordIds.forEach { autofillId ->
                    @Suppress("DEPRECATION")
                    setValue(autofillId, null, simplePresentation(label, packageName))
                }
            }
            .setId("murena-open-app-selection")
            .build()
    }

    @Suppress("DEPRECATION")
    private fun FillResponse.Builder.applyUnlockVaultAuthentication(
        loginFields: LoginFields
    ): FillResponse.Builder {
        val label = unlockDatasetLabel()
        val unlockIntent =
            Intent(this@MurenaAutoFillService, autofillDatasetAuthActivityClass()).apply {
                putExtra(AutofillDatasetAuthActivity.EXTRA_UNLOCK_ONLY, true)
                putExtra(AutofillDatasetAuthActivity.EXTRA_PACKAGE_NAME, loginFields.packageName)
                putExtra(AutofillDatasetAuthActivity.EXTRA_WEB_DOMAIN, loginFields.webDomain)
                putParcelableArrayListExtra(
                    AutofillDatasetAuthActivity.EXTRA_USERNAME_IDS,
                    ArrayList(loginFields.usernameIds)
                )
                putParcelableArrayListExtra(
                    AutofillDatasetAuthActivity.EXTRA_PASSWORD_IDS,
                    ArrayList(loginFields.passwordIds)
                )
            }
        val fieldIds = (loginFields.usernameIds + loginFields.passwordIds).distinct().toTypedArray()
        setAuthentication(
            fieldIds,
            PendingIntent.getActivity(
                this@MurenaAutoFillService,
                UNLOCK_VAULT_REQUEST_CODE,
                unlockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).intentSender,
            simplePresentation(label, packageName)
        )
        return this
    }

    private fun mutablePendingIntentFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
    }

    private fun buildClientState(loginFields: LoginFields): Bundle {
        return Bundle().apply {
            putString(CLIENT_STATE_PACKAGE, loginFields.packageName)
            putString(CLIENT_STATE_WEB_DOMAIN, loginFields.webDomain)
            putString(CLIENT_STATE_TARGET, loginFields.webDomain ?: loginFields.packageName)
            putParcelableArrayList(CLIENT_STATE_USERNAME_IDS, ArrayList(loginFields.usernameIds))
            putParcelableArrayList(CLIENT_STATE_PASSWORD_IDS, ArrayList(loginFields.passwordIds))
        }
    }

    private fun LoginFields.toPasswordQuery(): PasswordQuery {
        return PasswordQuery(
            source = PasswordRequestSource.AUTOFILL,
            packageName = packageName,
            webDomain = webDomain,
            origin = null,
            usernameHint = null,
            hasPasswordField = passwordIds.isNotEmpty()
        )
    }

    private fun buildUserData(credential: PasswordEntry): UserData {
        return UserData.Builder(
            "murena-user-data-${credential.id}",
            credential.username,
            "username"
        ).build()
    }

    private data class LoginFields(
        val packageName: String,
        val webDomain: String?,
        val usernameIds: List<AutofillId>,
        val passwordIds: List<AutofillId>,
        val triggerId: AutofillId?,
        val ignoredIds: List<AutofillId>
    )

    private class LoginFieldParser(
        private val structure: AssistStructure
    ) {
        private val usernameIds = mutableListOf<AutofillId>()
        private val passwordIds = mutableListOf<AutofillId>()
        private val webDomains = linkedMapOf<String, Int>()
        private val ignoredIds = mutableListOf<AutofillId>()
        private var lastTextId: AutofillId? = null
        private var usernameCandidateId: AutofillId? = null
        private var triggerId: AutofillId? = null

        fun parse(): LoginFields {
            for (index in 0 until structure.windowNodeCount) {
                parseNode(structure.getWindowNodeAt(index).rootViewNode)
            }

            if (usernameIds.isEmpty()) {
                usernameCandidateId?.let(usernameIds::add)
            }

            return LoginFields(
                packageName = structure.activityComponent.packageName,
                webDomain = webDomains
                    .filterKeys { it != "localhost" }
                    .maxByOrNull { it.value }
                    ?.key,
                usernameIds = usernameIds,
                passwordIds = passwordIds,
                triggerId = triggerId,
                ignoredIds = ignoredIds
            )
        }

        private fun parseNode(node: AssistStructure.ViewNode?) {
            if (node == null) return

            node.autofillId?.let { autofillId ->
                if (node.shouldIgnore()) {
                    ignoredIds.add(autofillId)
                }
                if (triggerId == null && node.isLikelyLoginTrigger()) {
                    triggerId = autofillId
                }

                when (node.fieldType()) {
                    FieldType.USERNAME -> usernameIds.add(autofillId)
                    FieldType.PASSWORD -> {
                        passwordIds.add(autofillId)
                        usernameCandidateId = lastTextId
                    }

                    FieldType.TEXT -> lastTextId = autofillId
                    null -> Unit
                }
            }

            node.webDomain?.let { domain ->
                webDomains[domain] = webDomains.getOrDefault(domain, 0) + 1
            }

            for (index in 0 until node.childCount) {
                parseNode(node.getChildAt(index))
            }
        }

        private fun AssistStructure.ViewNode.isLikelyLoginTrigger(): Boolean {
            val visibleText = text?.toString()
            return visibleText.containsAny("login", "log in", "sign in", "submit", "continue")
        }

        private fun AssistStructure.ViewNode.shouldIgnore(): Boolean {
            return hint.containsAny("search", "otp", "one-time", "verification") ||
                text.containsAny("search", "otp", "one-time", "verification") ||
                autofillHints?.any {
                    it.contains("otp", ignoreCase = true) ||
                        it.contains("one", ignoreCase = true)
                } == true
        }

        private fun AssistStructure.ViewNode.fieldType(): FieldType? {
            if (autofillType != View.AUTOFILL_TYPE_TEXT) return null

            autofillHints?.forEach { hint ->
                when (hint) {
                    View.AUTOFILL_HINT_USERNAME,
                    View.AUTOFILL_HINT_EMAIL_ADDRESS -> return FieldType.USERNAME

                    View.AUTOFILL_HINT_PASSWORD -> return FieldType.PASSWORD
                }
            }

            if (hasHtmlAttribute("type", "password") || inputType.isPasswordType()) {
                return FieldType.PASSWORD
            }

            if (
                hasHtmlAttribute("type", "email") ||
                hasHtmlAttribute("name", "email") ||
                hasHtmlAttribute("name", "mail") ||
                hasHtmlAttribute("name", "user") ||
                hasHtmlAttribute("name", "username") ||
                hint.containsAny("user", "mail", "email", "login")
            ) {
                return FieldType.USERNAME
            }

            if (inputType.isTextType()) {
                return FieldType.TEXT
            }

            return null
        }

        private fun AssistStructure.ViewNode.hasHtmlAttribute(
            attributeName: String,
            attributeValue: String
        ): Boolean {
            return htmlInfo?.attributes?.any { attribute ->
                attribute.first.equals(attributeName, ignoreCase = true) &&
                    attribute.second.equals(attributeValue, ignoreCase = true)
            } == true
        }

        private fun CharSequence?.containsAny(vararg needles: String): Boolean {
            val text = this?.toString()?.lowercase() ?: return false
            return needles.any(text::contains)
        }

        private fun Int.isTextType(): Boolean {
            return this and InputType.TYPE_CLASS_TEXT != 0
        }

        private fun Int.isPasswordType(): Boolean {
            val variation = this and (EditorInfo.TYPE_MASK_CLASS or EditorInfo.TYPE_MASK_VARIATION)
            return variation == (EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD) ||
                variation == (EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
                variation == (EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) ||
                variation == (EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD)
        }
    }

    private enum class FieldType {
        USERNAME,
        PASSWORD,
        TEXT
    }

    private fun AssistStructure.findTextValue(autofillId: AutofillId?): String? {
        if (autofillId == null) return null

        for (index in 0 until windowNodeCount) {
            findTextValue(getWindowNodeAt(index).rootViewNode, autofillId)?.let {
                return it
            }
        }

        return null
    }

    private fun List<android.service.autofill.FillContext>.findLatestTextValue(
        ids: List<AutofillId>
    ): String? {
        for (contextIndex in indices.reversed()) {
            val structure = this[contextIndex].structure
            ids.forEach { id ->
                structure.findTextValue(id)?.let { value ->
                    if (value.isNotBlank()) return value
                }
            }
        }
        return null
    }

    private fun findTextValue(
        node: AssistStructure.ViewNode?,
        autofillId: AutofillId
    ): String? {
        if (node == null) return null

        if (node.autofillId == autofillId) {
            val value = node.autofillValue
            if (value?.isText == true) {
                return value.textValue?.toString()
            }
        }

        for (index in 0 until node.childCount) {
            findTextValue(node.getChildAt(index), autofillId)?.let {
                return it
            }
        }

        return null
    }

    internal companion object {
        const val TAG = "MurenaAutoFillService"
        const val CLIENT_STATE_PACKAGE = "foundation.e.auto_fill.CLIENT_STATE_PACKAGE"
        const val CLIENT_STATE_WEB_DOMAIN = "foundation.e.auto_fill.CLIENT_STATE_WEB_DOMAIN"
        const val CLIENT_STATE_TARGET = "foundation.e.auto_fill.CLIENT_STATE_TARGET"
        const val CLIENT_STATE_USERNAME_IDS = "foundation.e.auto_fill.CLIENT_STATE_USERNAME_IDS"
        const val CLIENT_STATE_PASSWORD_IDS = "foundation.e.auto_fill.CLIENT_STATE_PASSWORD_IDS"
        const val AUTH_DATASET_REQUEST_CODE = 29001
        const val INLINE_PRESENTATION_REQUEST_CODE = 29002
        const val APP_SELECTION_REQUEST_CODE = 29003
        const val UNLOCK_VAULT_REQUEST_CODE = 29004
        const val DISABLE_AUTOFILL_DURATION_MILLIS = 60 * 60 * 1000L
        val NON_EMPTY_TEXT_PATTERN: Pattern = Pattern.compile(".+")
        val TRIM_TEXT_PATTERN: Pattern = Pattern.compile("^\\s*(.*?)\\s*$")
        fun buildCredentialDataset(
            context: android.content.Context,
            authActivityClass: Class<out Activity>,
            credential: PasswordEntry,
            usernameIds: List<AutofillId>,
            passwordIds: List<AutofillId>,
            label: String = credential.label,
            inlinePresentationSpec: InlinePresentationSpec? = null
        ): Dataset {
            return datasetBuilder(label, context.packageName)
                .apply {
                    usernameIds.forEach { autofillId ->
                        setCredentialField(
                            context = context,
                            autofillId = autofillId,
                            value = credential.username,
                            label = "${credential.label}: ${credential.username}",
                            authActivityClass = authActivityClass,
                            inlinePresentationSpec = inlinePresentationSpec
                        )
                    }
                    passwordIds.forEach { autofillId ->
                        setCredentialField(
                            context = context,
                            autofillId = autofillId,
                            value = credential.password.orEmpty(),
                            label = "${credential.label}: password",
                            authActivityClass = authActivityClass,
                            inlinePresentationSpec = inlinePresentationSpec
                        )
                    }
                }
                .setId("murena-${credential.id}")
                .build()
        }

        private fun Dataset.Builder.setCredentialField(
            context: android.content.Context,
            autofillId: AutofillId,
            value: String,
            label: String,
            authActivityClass: Class<out Activity>,
            inlinePresentationSpec: InlinePresentationSpec?
        ) {
            val valuePresentation = simplePresentation(label, context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val presentationsBuilder = Presentations.Builder()
                    .setMenuPresentation(valuePresentation)
                    .setDialogPresentation(valuePresentation)

                if (inlinePresentationSpec != null) {
                    presentationsBuilder.setInlinePresentation(
                        buildInlinePresentation(
                            context,
                            authActivityClass,
                            label,
                            inlinePresentationSpec
                        )
                    )
                }

                setField(
                    autofillId,
                    Field.Builder()
                        .setValue(AutofillValue.forText(value))
                        .setFilter(Pattern.compile(".*", Pattern.CASE_INSENSITIVE))
                        .setPresentations(presentationsBuilder.build())
                        .build()
                )
                return
            }

            @Suppress("DEPRECATION")
            if (inlinePresentationSpec != null) {
                setValue(
                    autofillId,
                    AutofillValue.forText(value),
                    valuePresentation,
                    buildInlinePresentation(
                        context,
                        authActivityClass,
                        label,
                        inlinePresentationSpec
                    )
                )
            } else {
                setValue(
                    autofillId,
                    AutofillValue.forText(value),
                    valuePresentation
                )
            }
        }

        private fun datasetBuilder(label: String, packageName: String): Dataset.Builder {
            val presentation = simplePresentation(label, packageName)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Dataset.Builder(
                    Presentations.Builder()
                        .setMenuPresentation(presentation)
                        .setDialogPresentation(presentation)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                Dataset.Builder(presentation)
            }
        }

        @SuppressLint("RestrictedApi")
        private fun buildInlinePresentation(
            context: android.content.Context,
            authActivityClass: Class<out Activity>,
            label: String,
            inlinePresentationSpec: InlinePresentationSpec
        ): InlinePresentation {
            val pendingIntent = PendingIntent.getActivity(
                context,
                INLINE_PRESENTATION_REQUEST_CODE,
                Intent(context, authActivityClass),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            return InlinePresentation(
                InlineSuggestionUi.newContentBuilder(pendingIntent)
                    .setTitle(label)
                    .setStartIcon(Icon.createWithResource(context, android.R.drawable.ic_lock_lock))
                    .build()
                    .slice,
                inlinePresentationSpec,
                false
            )
        }

        fun simplePresentation(text: String, packageName: String): RemoteViews {
            return RemoteViews(
                packageName,
                android.R.layout.simple_list_item_1
            )
                .apply {
                    setTextViewText(android.R.id.text1, text)
                }
        }
    }
}
