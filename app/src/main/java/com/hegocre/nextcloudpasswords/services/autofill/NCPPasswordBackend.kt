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
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.FoldersApi
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengeMasterKeyInvalidException
import com.hegocre.nextcloudpasswords.api.exceptions.PWDv1ChallengePasswordException
import com.hegocre.nextcloudpasswords.backupApp.OwnedCredentialResult
import com.hegocre.nextcloudpasswords.backupApp.OwnedLookup
import com.hegocre.nextcloudpasswords.backupApp.OwnedSave
import com.hegocre.nextcloudpasswords.backupApp.ownedCreateAllowed
import com.hegocre.nextcloudpasswords.backupApp.ownedSaveDecision
import com.hegocre.nextcloudpasswords.backupApp.toOwnedResult
import com.hegocre.nextcloudpasswords.backupApp.lookupWithSync
import com.hegocre.nextcloudpasswords.backupApp.selectOwnedEntry
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.PasswordController
import com.hegocre.nextcloudpasswords.data.password.RequestedPassword
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.databases.AppDatabase
import com.hegocre.nextcloudpasswords.utils.MasterPasswordMemoryStore
import com.hegocre.nextcloudpasswords.utils.Result
import com.hegocre.nextcloudpasswords.utils.SecureMasterPasswordStore
import com.hegocre.nextcloudpasswords.utils.decryptPasswords
import com.hegocre.nextcloudpasswords.utils.encryptValue
import com.hegocre.nextcloudpasswords.utils.hasActiveNetworkConnection
import com.hegocre.nextcloudpasswords.utils.sha1Hash
import foundation.e.autofill.GeneratePasswordResult
import foundation.e.autofill.MurenaPasswordBackend
import foundation.e.autofill.PasswordEntry
import foundation.e.autofill.PasswordEvent
import foundation.e.autofill.PasswordQuery
import foundation.e.autofill.PasswordQueryResult
import foundation.e.autofill.PasswordResolveRequest
import foundation.e.autofill.PasswordRequestSource
import foundation.e.autofill.PasswordSaveRequest
import foundation.e.autofill.PasswordSaveResult
import foundation.e.autofill.VaultUnlockRequest
import foundation.e.autofill.VaultUnlockResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class NCPAutofillSaveCandidate(
    val id: String,
    val label: String,
    val username: String,
    val url: String
)

private data class EntryUpdate(
    val password: String,
    val packageName: String?,
    val linkedWebsite: String? = null,
    val username: String? = null,
    val identityKey: String? = null,
    val readOnly: Boolean? = null
)

private data class UpdatedMetadata(
    val url: String,
    val customFields: String
)

private data class ExistingSaveLookup(
    val packageName: String?,
    val username: String?,
    val identityKey: String?,
    val password: String,
    val passwords: List<Password>,
    val candidates: List<String>
)

private sealed interface ExistingSaveSelection {
    data object NoMatch : ExistingSaveSelection
    data object NeedsUserInteraction : ExistingSaveSelection
    data class Update(val password: Password) : ExistingSaveSelection
}

@Suppress("LargeClass", "TooManyFunctions")
class NCPPasswordBackend(context: Context) : MurenaPasswordBackend {
    private val appContext = context.applicationContext
    private val userController = UserController.getInstance(appContext)
    private val passwordDatabase = AppDatabase.getInstance(appContext)
    private val matcher = NCPAutofillMatcher(appContext)
    private val serverPasswordGenerator = ServerPasswordGenerator(
        loadClient = { apiControllerOrNull() },
        prepareClient = { apiController -> ensureSessionOpen(apiController) },
        isVaultUnlocked = { isUnlocked() },
        isOnline = { appContext.hasActiveNetworkConnection() },
        requestPassword = { apiController ->
            apiController.generatePassword(
                strength = RequestedPassword.STRENGTH_MEDIUM,
                includeDigits = true,
                includeSymbols = true
            )
        }
    )

    override suspend fun query(request: PasswordQuery): PasswordQueryResult =
        withContext(Dispatchers.IO) {
            val savedPasswords = getVisiblePasswords()
            val vaultLocked = savedPasswords.isNotEmpty() && !isUnlocked()

            if (!userController.isLoggedIn) {
                val loginInProgress = NCPAutofillPendingSaveContinuation.isActive()
                Timber.d(
                    "query backend unavailable package=${request.packageName}, " +
                        "cachedPasswords=${savedPasswords.size}, vaultLocked=$vaultLocked, " +
                        "loginInProgress=$loginInProgress"
                )
                return@withContext PasswordQueryResult(
                    credentials = emptyList(),
                    savedPasswordCount = savedPasswords.size,
                    allowSavePrompt = !loginInProgress,
                    vaultLocked = vaultLocked
                )
            }

            if (request.hasUntrustedCredentialManagerBrowserContext()) {
                Timber.d(
                    "query ignored untrusted browser context package=${request.packageName}, " +
                        "cachedPasswords=${savedPasswords.size}"
                )
                return@withContext PasswordQueryResult(
                    credentials = emptyList(),
                    savedPasswordCount = savedPasswords.size,
                    allowSavePrompt = false,
                    vaultLocked = false
                )
            }

            if (vaultLocked) {
                Timber.d(
                    "query locked vault package=${request.packageName}, " +
                        "cachedPasswords=${savedPasswords.size}"
                )
                return@withContext PasswordQueryResult(
                    credentials = emptyList(),
                    savedPasswordCount = savedPasswords.size,
                    allowSavePrompt = true,
                    vaultLocked = true
                )
            }

            val matchingPasswords = matchingPasswords(savedPasswords, request)
            Timber.d(
                "query package=${request.packageName}, webDomain=${request.webDomain}, " +
                    "origin=${request.origin}, matches=${matchingPasswords.map { it.id }}"
            )

            PasswordQueryResult(
                credentials = matchingPasswords.map { it.toPasswordEntry() },
                savedPasswordCount = savedPasswords.size,
                allowSavePrompt = true,
                vaultLocked = false
            )
        }

    override suspend fun resolve(request: PasswordResolveRequest): PasswordEntry? =
        withContext(Dispatchers.IO) {
            val password = passwordDatabase.passwordDao.fetchAllPasswordsList()
                .firstOrNull { it.id == request.credentialId && !it.trashed && !it.hidden }
                ?: return@withContext null

            val candidates = matcher.candidates(request)
            if (candidates.isEmpty()) {
                Timber.d("resolve ignored: no trusted context for credential=${request.credentialId}")
                return@withContext null
            }

            val decryptedPassword = decryptIfUnlocked(listOf(password)).firstOrNull()
            if (decryptedPassword != null) {
                if (!matcher.matches(decryptedPassword, candidates)) {
                    Timber.d("resolve ignored: context mismatch for credential=${request.credentialId}")
                    return@withContext null
                }
                return@withContext decryptedPassword.toPasswordEntry()
            }

            password.toLockedPasswordEntry()
        }

    override suspend fun save(request: PasswordSaveRequest): PasswordSaveResult =
        withContext(Dispatchers.IO) {
            if (!userController.isLoggedIn) {
                return@withContext PasswordSaveResult.NeedsUnlock
            }

            if (request.hasUntrustedCredentialManagerBrowserContext()) {
                Timber.d("save ignored: untrusted browser context package=${request.packageName}")
                return@withContext PasswordSaveResult.Failed("Web origin could not be verified")
            }

            if (!isUnlocked()) {
                Timber.d("save needs unlock package=${request.packageName}, username=${request.username}")
                return@withContext PasswordSaveResult.NeedsUnlock
            }

            val apiController = apiControllerOrNull()
                ?: return@withContext PasswordSaveResult.Failed("No account is configured")
            if (!ensureSessionOpen(apiController)) return@withContext PasswordSaveResult.NeedsUnlock

            ensureInitialPasswordSync()

            val existingSaveResult = handleExistingEntryForSave(
                request = request,
                apiController = apiController
            )
            if (existingSaveResult != null) {
                Timber.d("save handled by existing entry result=$existingSaveResult")
                return@withContext existingSaveResult
            }

            if (request.username.isNullOrBlank()) {
                val candidates = saveInteractionCandidates(request)
                saveResultForMissingUsername(candidates.size)?.let { result ->
                    Timber.d("save needs user selection for password-only request")
                    return@withContext result
                }

                Timber.d(
                    "save creating new password-only entry package=${request.packageName}, " +
                        "webDomain=${request.webDomain}, origin=${request.origin}"
                )
            }

            Timber.d(
                "save creating new entry package=${request.packageName}, " +
                    "usernamePresent=${request.username?.isNotBlank() == true}"
            )
            createNewPassword(request, apiController)
        }

    override suspend fun unlock(request: VaultUnlockRequest): VaultUnlockResult =
        withContext(Dispatchers.IO) {
            val secret = request.secret?.takeIf { it.isNotBlank() }
                ?: return@withContext VaultUnlockResult.Canceled
            val apiController = apiControllerOrNull()
                ?: return@withContext VaultUnlockResult.Failed("No account is configured")

            val unlocked = runCatching {
                apiController.openSession(secret)
            }.getOrElse { error ->
                Timber.e(error,"Failed to unlock vault")
                false
            }

            if (!unlocked) {
                return@withContext VaultUnlockResult.Failed("Could not unlock vault")
            }

            MasterPasswordMemoryStore.set(secret)
            PasswordController.getInstance(appContext).syncPasswords()
            VaultUnlockResult.Unlocked
        }

    override suspend fun report(event: PasswordEvent) = Unit

    override suspend fun generatePassword(): GeneratePasswordResult = withContext(Dispatchers.IO) {
        serverPasswordGenerator.generate()
    }

    suspend fun saveInteractionCandidates(request: PasswordSaveRequest): List<NCPAutofillSaveCandidate> =
        withContext(Dispatchers.IO) {
            val candidates = matcher.candidates(request)
            if (candidates.isEmpty()) return@withContext emptyList()
            val decryptedPasswords = getDecryptedVisiblePasswords()
            val username = request.username?.takeIf { it.isNotBlank() }

            decryptedPasswords
                .filter { password ->
                    (username == null || password.username.equals(username, ignoreCase = true)) &&
                        matcher.matches(password, candidates)
                }
                .map {
                    NCPAutofillSaveCandidate(
                        id = it.id,
                        label = it.label,
                        username = it.username,
                        url = it.url
                    )
                }
        }

    suspend fun saveFromUserInteraction(
        request: PasswordSaveRequest,
        selectedCredentialId: String?
    ): PasswordSaveResult = withContext(Dispatchers.IO) {
        if (request.hasUntrustedCredentialManagerBrowserContext()) {
            Timber.d("save interaction ignored: untrusted browser context package=${request.packageName}")
            return@withContext PasswordSaveResult.Failed("Web origin could not be verified")
        }

        val apiController = apiControllerOrNull()
            ?: return@withContext PasswordSaveResult.Failed("No account is configured")
        if (!ensureSessionOpen(apiController)) return@withContext PasswordSaveResult.NeedsUnlock

        if (selectedCredentialId == null) {
            Timber.d(
                "save creating new entry from user interaction package=${request.packageName}, " +
                    "usernamePresent=${request.username?.isNotBlank() == true}"
            )
            return@withContext createNewPassword(request, apiController)
        }

        val password = passwordDatabase.passwordDao.fetchAllPasswordsList()
            .firstOrNull { it.id == selectedCredentialId && !it.trashed && !it.hidden }
            ?: return@withContext PasswordSaveResult.Failed("Selected password was not found")
        val decryptedPassword = decryptIfUnlocked(listOf(password)).firstOrNull()
            ?: return@withContext PasswordSaveResult.NeedsUnlock

        Timber.d("save updating selected existing id=$selectedCredentialId")
        updateExistingPassword(
            password = decryptedPassword,
            apiController = apiController,
            entryUpdate = EntryUpdate(
                password = request.password,
                packageName = request.appPackageNameForLink(),
                identityKey = request.identityKey?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun completePendingSave(
        pendingSave: NCPAutofillPendingSaveStore.PendingSave
    ): PasswordSaveResult {
        return if (pendingSave.createNew || pendingSave.selectedCredentialId != null) {
            saveFromUserInteraction(pendingSave.request, pendingSave.selectedCredentialId)
        } else {
            save(pendingSave.request)
        }
    }

    suspend fun findOwned(packageName: String, key: String): OwnedCredentialResult =
        withContext(Dispatchers.IO) {
            val apiController = unlockedApiControllerOrNull()
                ?: return@withContext OwnedCredentialResult.Locked
            when (val lookup = lookupOwned(apiController, packageName, key)) {
                is OwnedLookup.Hit -> OwnedCredentialResult.Found(lookup.password.password)
                OwnedLookup.Miss -> OwnedCredentialResult.NotFound
                OwnedLookup.SyncFailed -> OwnedCredentialResult.Failed(retryable = true)
            }
        }

    @Suppress("LongParameterList")
    suspend fun saveOwned(
        packageName: String,
        key: String,
        username: String,
        secret: String,
        replace: Boolean,
        readOnly: Boolean
    ): OwnedCredentialResult = withContext(Dispatchers.IO) {
        val apiController = unlockedApiControllerOrNull()
            ?: return@withContext OwnedCredentialResult.Locked
        val existing = when (val lookup = lookupOwned(apiController, packageName, key)) {
            OwnedLookup.SyncFailed -> return@withContext OwnedCredentialResult.Failed(retryable = true)
            OwnedLookup.Miss -> null
            is OwnedLookup.Hit -> lookup.password
        }
        when (val decision = ownedSaveDecision(existing, secret, username, replace, readOnly)) {
            OwnedSave.Create -> createOwned(apiController, packageName, key, username, secret, readOnly)
            is OwnedSave.ReturnStored -> OwnedCredentialResult.Found(decision.secret)
            OwnedSave.Update -> updateExistingPassword(
                password = checkNotNull(existing),
                apiController = apiController,
                entryUpdate = EntryUpdate(
                    password = secret,
                    packageName = packageName,
                    username = username,
                    identityKey = key,
                    readOnly = readOnly
                )
            ).toOwnedResult(secret)
        }
    }

    private fun apiControllerOrNull(): ApiController? {
        if (!userController.isLoggedIn) return null
        return runCatching { ApiController.getInstance(appContext) }.getOrNull()
    }

    private fun isUnlocked(): Boolean {
        return !MasterPasswordMemoryStore.get().isNullOrBlank() ||
            apiControllerOrNull()?.sessionOpen?.value == true
    }

    private suspend fun ensureInitialPasswordSync() {
        if (passwordDatabase.passwordDao.hasVisiblePasswords()) return

        runCatching { PasswordController.getInstance(appContext).syncPasswords() }
            .onFailure {
                if (it is CancellationException) throw it
                Timber.w(it, "initial password sync before save failed")
            }
    }

    private suspend fun ensureSessionOpen(apiController: ApiController): Boolean {
        if (apiController.sessionOpen.value) return true
        val masterPassword = MasterPasswordMemoryStore.get()?.takeIf { it.isNotBlank() }
            ?: return false
        return runCatching {
            apiController.openSession(masterPassword)
        }.getOrElse { error ->
            Timber.d("stored master password failed to open session: ${error.javaClass.simpleName}")
            if (error.invalidatesStoredMasterPassword()) {
                MasterPasswordMemoryStore.clear()
                SecureMasterPasswordStore(appContext).clear()
            }
            false
        }
    }

    private suspend fun unlockedApiControllerOrNull(): ApiController? {
        val apiController = apiControllerOrNull() ?: return null
        if (!ensureSessionOpen(apiController)) return null
        return apiController.takeIf { it.isEndToEndEncryptionKeyAvailable() }
    }

    private suspend fun lookupOwned(
        apiController: ApiController,
        packageName: String,
        key: String
    ): OwnedLookup = lookupWithSync(
        lookup = { selectOwnedEntry(decryptIfUnlocked(getVisiblePasswords()), packageName, key) },
        sync = { syncFromServer(apiController) }
    )

    private suspend fun syncFromServer(apiController: ApiController): Boolean {
        val result = apiController.listPasswords()
        if (result !is Result.Success) return false
        passwordDatabase.passwordDao.syncWithRemote(result.data)
        return true
    }

    @Suppress("LongParameterList")
    private suspend fun createOwned(
        apiController: ApiController,
        packageName: String,
        key: String,
        username: String,
        secret: String,
        readOnly: Boolean
    ): OwnedCredentialResult {
        if (!ownedCreateAllowed(apiController.serverSettings.value?.encryptionCse)) {
            return OwnedCredentialResult.Failed(retryable = false)
        }
        val customFields = NCPAutofillMetadata.withReadOnly(
            NCPAutofillMetadata.withIdentityKey(
                NCPAutofillMetadata.withPackage("[]", packageName),
                key
            ),
            readOnly
        )
        val created = apiController.createPassword(
            newPassword(
                apiController = apiController,
                label = applicationLabel(packageName) ?: packageName,
                url = "",
                username = username,
                password = secret,
                customFields = customFields
            )
        )
        if (!created) return OwnedCredentialResult.Failed(retryable = true)
        syncFromServer(apiController)
        return OwnedCredentialResult.Found(secret)
    }

    private fun Throwable.invalidatesStoredMasterPassword(): Boolean {
        return this is PWDv1ChallengeMasterKeyInvalidException ||
            this is PWDv1ChallengePasswordException
    }

    private suspend fun matchingPasswords(
        passwords: List<Password>,
        request: PasswordQuery
    ): List<Password> {
        val candidates = matcher.candidates(request)
        if (candidates.isEmpty()) return emptyList()

        val decrypted = decryptIfUnlocked(passwords).excludeAppManaged()
        return decrypted.filter { password ->
            matcher.matches(password, candidates)
        }
    }

    private suspend fun getVisiblePasswords(): List<Password> {
        return passwordDatabase.passwordDao.fetchAllPasswordsList()
            .filter { !it.trashed && !it.hidden }
    }

    private suspend fun getDecryptedVisiblePasswords(): List<Password> {
        return decryptIfUnlocked(getVisiblePasswords()).excludeAppManaged()
    }

    private suspend fun decryptIfUnlocked(passwords: List<Password>): List<Password> {
        val apiController = apiControllerOrNull() ?: return emptyList()
        if (!isUnlocked()) return emptyList()
        return passwords.decryptPasswords(apiController.currentKeychain())
    }

    private fun Password.toPasswordEntry(): PasswordEntry {
        return PasswordEntry(
            id = id,
            username = username,
            password = password,
            displayName = label,
            locked = false
        )
    }

    private fun Password.toLockedPasswordEntry(): PasswordEntry {
        return PasswordEntry(
            id = id,
            username = LOCKED_USERNAME,
            password = null,
            displayName = LOCKED_DISPLAY_NAME,
            locked = true
        )
    }

    private suspend fun handleExistingEntryForSave(
        request: PasswordSaveRequest,
        apiController: ApiController
    ): PasswordSaveResult? {
        val lookup = existingSaveLookup(request) ?: return null

        return identityKeySaveResult(lookup, apiController)
            ?: exactPasswordMatchSaveResult(lookup, apiController)
            ?: matchingUsernameSaveResult(lookup, apiController)
    }

    private suspend fun existingSaveLookup(request: PasswordSaveRequest): ExistingSaveLookup? {
        val username = request.username?.takeIf { it.isNotBlank() }
        val identityKey = request.identityKey?.takeIf { it.isNotBlank() }
        if (username == null && identityKey == null) return null

        return ExistingSaveLookup(
            packageName = request.appPackageNameForLink(),
            username = username,
            identityKey = identityKey,
            password = request.password,
            passwords = getDecryptedVisiblePasswords(),
            candidates = matcher.candidates(request)
        )
    }

    private suspend fun identityKeySaveResult(
        lookup: ExistingSaveLookup,
        apiController: ApiController
    ): PasswordSaveResult? {
        val identityKey = lookup.identityKey ?: return null
        val packageName = lookup.packageName ?: return null

        val claimed = lookup.passwords.firstOrNull { password ->
            matcher.hasIdentityKey(password, identityKey) &&
                matcher.hasPackage(password, packageName)
        } ?: return null

        val usernameUnchanged = lookup.username == null ||
            claimed.username.equals(lookup.username, ignoreCase = true)
        if (claimed.password == lookup.password && usernameUnchanged) {
            Timber.d("save duplicate ignored for claimed entry id=${claimed.id}")
            return PasswordSaveResult.DuplicateIgnored
        }

        Timber.d("save updating claimed entry id=${claimed.id}")
        return updateExistingPassword(
            password = claimed,
            apiController = apiController,
            entryUpdate = EntryUpdate(
                password = lookup.password,
                packageName = lookup.packageName,
                username = lookup.username,
                identityKey = identityKey
            )
        )
    }

    private fun Password.isClaimableBy(identityKey: String?): Boolean {
        val owner = NCPAutofillMetadata.identityKey(customFields) ?: return true
        return owner == identityKey
    }

    private suspend fun exactPasswordMatchSaveResult(
        lookup: ExistingSaveLookup,
        apiController: ApiController
    ): PasswordSaveResult? {
        val username = lookup.username ?: return null
        val exactPasswordMatch = lookup.passwords.firstOrNull { password ->
            password.username.equals(username, ignoreCase = true) &&
                password.password == lookup.password &&
                password.isClaimableBy(lookup.identityKey) &&
                matcher.matches(password, lookup.candidates)
        } ?: return null

        val alreadyOwned = lookup.identityKey == null ||
            NCPAutofillMetadata.identityKey(exactPasswordMatch.customFields) != null
        return if (
            alreadyOwned &&
            (lookup.packageName == null || matcher.hasPackage(exactPasswordMatch, lookup.packageName))
        ) {
            Timber.d("save duplicate ignored id=${exactPasswordMatch.id}")
            PasswordSaveResult.DuplicateIgnored
        } else {
            Timber.d("save linking/claiming existing id=${exactPasswordMatch.id}")
            updateExistingPassword(
                password = exactPasswordMatch,
                apiController = apiController,
                entryUpdate = EntryUpdate(
                    password = exactPasswordMatch.password,
                    packageName = lookup.packageName,
                    identityKey = lookup.identityKey
                )
            )
        }
    }

    private suspend fun matchingUsernameSaveResult(
        lookup: ExistingSaveLookup,
        apiController: ApiController
    ): PasswordSaveResult? {
        val matchingUsernameEntries = lookup.passwords.filter { password ->
            password.username.equals(lookup.username, ignoreCase = true) &&
                password.isClaimableBy(lookup.identityKey) &&
                matcher.matches(password, lookup.candidates)
        }

        return when (
            val selection = matchingUsernameEntries.existingSaveSelection(lookup.packageName)
        ) {
            ExistingSaveSelection.NoMatch -> null
            ExistingSaveSelection.NeedsUserInteraction -> {
                Timber.d("save needs user selection ids=${matchingUsernameEntries.map { it.id }}")
                PasswordSaveResult.NeedsUserInteraction(
                    "Multiple matching passwords found; select manually to update one"
                )
            }
            is ExistingSaveSelection.Update -> updateMatchingUsernameEntry(
                password = selection.password,
                lookup = lookup,
                apiController = apiController
            )
        }
    }

    private fun List<Password>.existingSaveSelection(packageName: String?): ExistingSaveSelection {
        return when (size) {
            0 -> ExistingSaveSelection.NoMatch
            1 -> ExistingSaveSelection.Update(single())
            else -> packageName
                ?.let { name -> filter { matcher.hasPackage(it, name) } }
                ?.takeIf { it.size == 1 }
                ?.single()
                ?.let { ExistingSaveSelection.Update(it) }
                ?: ExistingSaveSelection.NeedsUserInteraction
        }
    }

    private suspend fun updateMatchingUsernameEntry(
        password: Password,
        lookup: ExistingSaveLookup,
        apiController: ApiController
    ): PasswordSaveResult {
        Timber.d("save updating existing id=${password.id}")
        return updateExistingPassword(
            password = password,
            apiController = apiController,
            entryUpdate = EntryUpdate(
                password = lookup.password,
                packageName = lookup.packageName,
                identityKey = lookup.identityKey
            )
        )
    }

    private suspend fun updateExistingPassword(
        password: Password,
        apiController: ApiController,
        entryUpdate: EntryUpdate
    ): PasswordSaveResult {
        return if (apiController.updatePassword(
                password.toUpdatedPassword(apiController, entryUpdate)
            )
        ) {
            PasswordController.getInstance(appContext).syncPasswords()
            PasswordSaveResult.Saved
        } else {
            retryExistingPasswordUpdate(password, apiController, entryUpdate)
        }
    }

    private suspend fun retryExistingPasswordUpdate(
        password: Password,
        apiController: ApiController,
        entryUpdate: EntryUpdate
    ): PasswordSaveResult {
        Timber.d("update failed for id=${password.id}; syncing and retrying once")
        PasswordController.getInstance(appContext).syncPasswords()

        val latestPassword = passwordDatabase.passwordDao.fetchAllPasswordsList()
            .firstOrNull { it.id == password.id && !it.trashed && !it.hidden }
            ?.let { decryptIfUnlocked(listOf(it)).firstOrNull() }

        return if (latestPassword == null) {
            PasswordSaveResult.Failed("Could not refresh existing password")
        } else {
            updateRefreshedExistingPassword(
                password = latestPassword,
                apiController = apiController,
                entryUpdate = entryUpdate
            )
        }
    }

    private suspend fun updateRefreshedExistingPassword(
        password: Password,
        apiController: ApiController,
        entryUpdate: EntryUpdate
    ): PasswordSaveResult {
        return if (apiController.updatePassword(
                password.toUpdatedPassword(apiController, entryUpdate)
            )
        ) {
            PasswordController.getInstance(appContext).syncPasswords()
            PasswordSaveResult.Saved
        } else {
            PasswordSaveResult.Failed("Could not update existing password")
        }
    }

    private suspend fun createNewPassword(
        request: PasswordSaveRequest,
        apiController: ApiController
    ): PasswordSaveResult {
        val created = apiController.createPassword(request.toNewPassword(apiController))
        if (!created) {
            return PasswordSaveResult.Failed("Could not save password")
        }

        PasswordController.getInstance(appContext).syncPasswords()
        return PasswordSaveResult.Saved
    }

    private fun Password.updatedMetadata(update: EntryUpdate): UpdatedMetadata {
        val websiteUpdate = update.linkedWebsite
            ?.takeIf { it.isNotBlank() }
            ?.let { NCPAutofillMetadata.linkWebsite(url, customFields, it) }
        val baseFields = websiteUpdate?.customFieldsJson ?: customFields
        val withPackage = update.packageName
            ?.takeIf { it.isNotBlank() }
            ?.let { NCPAutofillMetadata.withPackage(baseFields, it) }
            ?: baseFields
        val withIdentity = update.identityKey
            ?.takeIf { it.isNotBlank() }
            ?.let { NCPAutofillMetadata.withIdentityKey(withPackage, it) }
            ?: withPackage
        val withReadOnly = update.readOnly
            ?.let { NCPAutofillMetadata.withReadOnly(withIdentity, it) }
            ?: withIdentity

        return UpdatedMetadata(
            url = websiteUpdate?.url ?: url,
            customFields = withReadOnly
        )
    }

    private fun Password.toUpdatedPassword(
        apiController: ApiController,
        entryUpdate: EntryUpdate
    ): UpdatedPassword {
        val updatedPassword = entryUpdate.password
        val serverSettings = apiController.serverSettings.value
        val currentKeychain = apiController.currentKeychain()
        val shouldEncrypt = currentKeychain != null && cseType == ApiController.CSE_TYPE
        val hashLength = serverSettings?.passwordSecurityHash ?: DEFAULT_PASSWORD_HASH_LENGTH
        val updatedEdited = if (updatedPassword == password) edited else 0
        val metadata = updatedMetadata(entryUpdate)
        val updatedUrl = metadata.url
        val updatedCustomFields = metadata.customFields
        val updatedUsername = entryUpdate.username?.takeIf { it.isNotBlank() } ?: username

        return if (shouldEncrypt) {
            UpdatedPassword(
                id = id,
                revision = revision,
                password = updatedPassword.encryptValue(currentKeychain.current, currentKeychain),
                label = label.encryptValue(currentKeychain.current, currentKeychain),
                username = updatedUsername.encryptValue(currentKeychain.current, currentKeychain),
                url = updatedUrl.encryptValue(currentKeychain.current, currentKeychain),
                notes = notes.encryptValue(currentKeychain.current, currentKeychain),
                customFields = updatedCustomFields.encryptValue(
                    currentKeychain.current,
                    currentKeychain
                ),
                hash = updatedPassword.sha1Hash().take(hashLength),
                cseType = ApiController.CSE_TYPE,
                cseKey = currentKeychain.current,
                folder = folder,
                edited = updatedEdited,
                hidden = hidden,
                favorite = favorite
            )
        } else {
            UpdatedPassword(
                id = id,
                revision = revision,
                password = updatedPassword,
                label = label,
                username = updatedUsername,
                url = updatedUrl,
                notes = notes,
                customFields = updatedCustomFields,
                hash = updatedPassword.sha1Hash().take(hashLength),
                cseType = "none",
                cseKey = "",
                folder = folder,
                edited = updatedEdited,
                hidden = hidden,
                favorite = favorite
            )
        }
    }

    suspend fun linkWebsiteForManualSelection(
        password: Password,
        website: String
    ): PasswordSaveResult = withContext(Dispatchers.IO) {
        if (website.isBlank()) {
            return@withContext PasswordSaveResult.Failed("No website available for manual autofill")
        }

        val apiController = apiControllerOrNull()
            ?: return@withContext PasswordSaveResult.Failed("No account is configured")
        if (!ensureSessionOpen(apiController)) return@withContext PasswordSaveResult.NeedsUnlock

        updateExistingPassword(
            password = password,
            apiController = apiController,
            entryUpdate = EntryUpdate(
                password = password.password,
                packageName = null,
                linkedWebsite = website
            )
        )
    }

    private fun PasswordSaveRequest.toNewPassword(apiController: ApiController): NewPassword {
        val packageFields = appPackageNameForLink()?.let { packageName ->
            NCPAutofillMetadata.withPackage("[]", packageName)
        } ?: "[]"
        val customFields = identityKey
            ?.takeIf { it.isNotBlank() }
            ?.let { NCPAutofillMetadata.withIdentityKey(packageFields, it) }
            ?: packageFields
        return newPassword(
            apiController = apiController,
            label = saveLabel(),
            url = saveUrl(),
            username = username.orEmpty(),
            password = password,
            customFields = customFields
        )
    }

    @Suppress("LongParameterList")
    private fun newPassword(
        apiController: ApiController,
        label: String,
        url: String,
        username: String,
        password: String,
        customFields: String
    ): NewPassword {
        val serverSettings = apiController.serverSettings.value
        val currentKeychain = apiController.currentKeychain()
        val shouldEncrypt = currentKeychain != null && serverSettings?.encryptionCse != 0
        val notes = ""
        val hashLength = serverSettings?.passwordSecurityHash ?: DEFAULT_PASSWORD_HASH_LENGTH

        return if (shouldEncrypt) {
            NewPassword(
                password = password.encryptValue(currentKeychain.current, currentKeychain),
                label = label.encryptValue(currentKeychain.current, currentKeychain),
                username = username.encryptValue(currentKeychain.current, currentKeychain),
                url = url.encryptValue(currentKeychain.current, currentKeychain),
                notes = notes.encryptValue(currentKeychain.current, currentKeychain),
                customFields = customFields.encryptValue(currentKeychain.current, currentKeychain),
                hash = password.sha1Hash().take(hashLength),
                cseType = ApiController.CSE_TYPE,
                cseKey = currentKeychain.current,
                folder = FoldersApi.DEFAULT_FOLDER_UUID,
                edited = 0,
                hidden = false,
                favorite = false
            )
        } else {
            NewPassword(
                password = password,
                label = label,
                username = username,
                url = url,
                notes = notes,
                customFields = customFields,
                hash = password.sha1Hash().take(hashLength),
                cseType = "none",
                cseKey = "",
                folder = FoldersApi.DEFAULT_FOLDER_UUID,
                edited = 0,
                hidden = false,
                favorite = false
            )
        }
    }

    private fun PasswordSaveRequest.saveLabel(): String {
        return webDomain?.takeIf { it.isNotBlank() }
            ?: origin?.takeIf { it.isNotBlank() }
            ?: appPackageNameForLink()?.let { applicationLabel(it) }
            ?: username?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SAVE_LABEL
    }

    private fun PasswordSaveRequest.saveUrl(): String {
        return NCPAutofillMetadata.normalizeWebsite(webDomain)
            ?: origin?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun applicationLabel(packageName: String): String? {
        return runCatching {
            val appInfo = appContext.packageManager.getApplicationInfoCompat(packageName)
            appContext.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

    private fun PasswordQuery.hasUntrustedCredentialManagerBrowserContext(): Boolean {
        return source == PasswordRequestSource.CREDENTIAL_MANAGER &&
            (isWebOriginRequest || matcher.isKnownBrowserPackage(packageName)) &&
            webDomain.isNullOrBlank() &&
            origin.isNullOrBlank()
    }

    private fun PasswordSaveRequest.hasUntrustedCredentialManagerBrowserContext(): Boolean {
        return source == PasswordRequestSource.CREDENTIAL_MANAGER &&
            (isWebOriginRequest || matcher.isKnownBrowserPackage(packageName)) &&
            webDomain.isNullOrBlank() &&
            origin.isNullOrBlank()
    }

    private fun PasswordSaveRequest.hasWebContext(): Boolean {
        return isWebOriginRequest || !webDomain.isNullOrBlank() || !origin.isNullOrBlank()
    }

    private fun PasswordSaveRequest.appPackageNameForLink(): String? {
        return packageName?.takeIf { it.isNotBlank() && !hasWebContext() }
    }

    private companion object {
        const val DEFAULT_SAVE_LABEL = "Autofill password"
        const val DEFAULT_PASSWORD_HASH_LENGTH = 40
        const val LOCKED_DISPLAY_NAME = "Locked password"
        const val LOCKED_USERNAME = "Unlock to view"
    }
}

internal fun List<Password>.excludeAppManaged(): List<Password> {
    return filterNot(Password::isAppManaged)
}

internal fun saveResultForMissingUsername(candidateCount: Int): PasswordSaveResult? {
    return if (candidateCount > 0) {
        PasswordSaveResult.NeedsUserInteraction("Select the password to update")
    } else {
        null
    }
}
