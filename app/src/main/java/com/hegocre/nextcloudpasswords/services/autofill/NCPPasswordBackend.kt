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
import android.util.Log
import com.hegocre.nextcloudpasswords.api.ApiController
import com.hegocre.nextcloudpasswords.api.FoldersApi
import com.hegocre.nextcloudpasswords.BuildConfig
import com.hegocre.nextcloudpasswords.data.password.NewPassword
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.data.password.PasswordController
import com.hegocre.nextcloudpasswords.data.password.UpdatedPassword
import com.hegocre.nextcloudpasswords.data.user.UserController
import com.hegocre.nextcloudpasswords.databases.AppDatabase
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import com.hegocre.nextcloudpasswords.utils.decryptPasswords
import com.hegocre.nextcloudpasswords.utils.encryptValue
import com.hegocre.nextcloudpasswords.utils.sha1Hash
import foundation.e.auto_fill.MurenaPasswordBackend
import foundation.e.auto_fill.PasswordEntry
import foundation.e.auto_fill.PasswordEvent
import foundation.e.auto_fill.PasswordQuery
import foundation.e.auto_fill.PasswordQueryResult
import foundation.e.auto_fill.PasswordResolveRequest
import foundation.e.auto_fill.PasswordSaveRequest
import foundation.e.auto_fill.PasswordSaveResult
import foundation.e.auto_fill.VaultUnlockRequest
import foundation.e.auto_fill.VaultUnlockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NCPAutofillSaveCandidate(
    val id: String,
    val label: String,
    val username: String,
    val url: String
)

class NCPPasswordBackend(context: Context) : MurenaPasswordBackend {
    private val appContext = context.applicationContext
    private val preferencesManager = PreferencesManager.getInstance(appContext)
    private val userController = UserController.getInstance(appContext)
    private val passwordDatabase = AppDatabase.getInstance(appContext)
    private val matcher = NCPAutofillMatcher(appContext)

    override suspend fun query(request: PasswordQuery): PasswordQueryResult =
        withContext(Dispatchers.IO) {
            val savedPasswords = passwordDatabase.passwordDao.fetchAllPasswordsList()
                .filter { !it.trashed && !it.hidden && !it.isBackupAppKey() }
            val vaultLocked = savedPasswords.isNotEmpty() && !isUnlocked()

            if (!userController.isLoggedIn) {
                debugLog(
                    "query backend unavailable package=${request.packageName}, " +
                        "cachedPasswords=${savedPasswords.size}, vaultLocked=$vaultLocked"
                )
                return@withContext PasswordQueryResult(
                    credentials = emptyList(),
                    savedPasswordCount = savedPasswords.size,
                    allowSavePrompt = false,
                    vaultLocked = vaultLocked
                )
            }

            if (vaultLocked) {
                debugLog(
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
            debugLog(
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

            decryptIfUnlocked(listOf(password)).firstOrNull()?.toPasswordEntry()
                ?: password.toLockedPasswordEntry()
        }

    override suspend fun save(request: PasswordSaveRequest): PasswordSaveResult =
        withContext(Dispatchers.IO) {
            if (!userController.isLoggedIn) {
                return@withContext PasswordSaveResult.Failed("No account is configured")
            }

            if (!isUnlocked()) {
                debugLog("save needs unlock package=${request.packageName}, username=${request.username}")
                return@withContext PasswordSaveResult.NeedsUnlock
            }

            val apiController = apiControllerOrNull()
                ?: return@withContext PasswordSaveResult.Failed("No account is configured")
            if (!ensureSessionOpen(apiController)) return@withContext PasswordSaveResult.NeedsUnlock

            val existingSaveResult = handleExistingEntryForSave(
                request = request,
                apiController = apiController
            )
            if (existingSaveResult != null) {
                debugLog("save handled by existing entry result=$existingSaveResult")
                return@withContext existingSaveResult
            }

            if (request.username.isNullOrBlank()) {
                debugLog("save ignored: username is missing package=${request.packageName}")
                return@withContext PasswordSaveResult.Failed("Username is required to save a password")
            }

            debugLog("save creating new entry package=${request.packageName}, username=${request.username}")
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
                debugWarn("Failed to unlock vault", error)
                false
            }

            if (!unlocked) {
                return@withContext VaultUnlockResult.Failed("Could not unlock vault")
            }

            preferencesManager.setMasterPassword(secret)
            PasswordController.getInstance(appContext).syncPasswords()
            VaultUnlockResult.Unlocked
        }

    override suspend fun report(event: PasswordEvent) = Unit

    suspend fun saveInteractionCandidates(request: PasswordSaveRequest): List<NCPAutofillSaveCandidate> =
        withContext(Dispatchers.IO) {
            val username = request.username?.takeIf { it.isNotBlank() }
                ?: return@withContext emptyList()
            val candidates = matcher.candidates(request)
            val savedPasswords = passwordDatabase.passwordDao.fetchAllPasswordsList()
                .filter { !it.trashed && !it.hidden && !it.isBackupAppKey() }
            val decryptedPasswords = decryptIfUnlocked(savedPasswords)

            decryptedPasswords
                .filter { password ->
                    password.username.equals(username, ignoreCase = true) &&
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
        val apiController = apiControllerOrNull()
            ?: return@withContext PasswordSaveResult.Failed("No account is configured")
        if (!ensureSessionOpen(apiController)) return@withContext PasswordSaveResult.NeedsUnlock

        if (selectedCredentialId == null) {
            if (request.username.isNullOrBlank()) {
                debugLog("save ignored: username is missing package=${request.packageName}")
                return@withContext PasswordSaveResult.Failed("Username is required to save a password")
            }
            debugLog("save creating new entry from user interaction package=${request.packageName}, username=${request.username}")
            return@withContext createNewPassword(request, apiController)
        }

        val password = passwordDatabase.passwordDao.fetchAllPasswordsList()
            .firstOrNull { it.id == selectedCredentialId && !it.trashed && !it.hidden }
            ?: return@withContext PasswordSaveResult.Failed("Selected password was not found")
        val decryptedPassword = decryptIfUnlocked(listOf(password)).firstOrNull()
            ?: return@withContext PasswordSaveResult.NeedsUnlock

        debugLog("save updating selected existing id=$selectedCredentialId")
        updateExistingPassword(
            password = decryptedPassword,
            apiController = apiController,
            updatedPassword = request.password,
            packageName = request.packageName
        )
    }

    private fun apiControllerOrNull(): ApiController? {
        if (!userController.isLoggedIn) return null
        return runCatching { ApiController.getInstance(appContext) }.getOrNull()
    }

    private fun isUnlocked(): Boolean {
        return !preferencesManager.getMasterPassword().isNullOrBlank() ||
            apiControllerOrNull()?.sessionOpen?.value == true
    }

    private suspend fun ensureSessionOpen(apiController: ApiController): Boolean {
        if (apiController.sessionOpen.value) return true
        val masterPassword = preferencesManager.getMasterPassword()?.takeIf { it.isNotBlank() }
            ?: return false
        return runCatching {
            apiController.openSession(masterPassword)
        }.getOrElse { error ->
            debugLog("stored master password failed to open session: ${error.javaClass.simpleName}")
            preferencesManager.setMasterPassword(null)
            false
        }
    }

    private suspend fun matchingPasswords(
        passwords: List<Password>,
        request: PasswordQuery
    ): List<Password> {
        val candidates = matcher.candidates(request)
        if (candidates.isEmpty()) return emptyList()

        val decrypted = decryptIfUnlocked(passwords)
        return decrypted.filter { password ->
            matcher.matches(password, candidates)
        }
    }

    private suspend fun decryptIfUnlocked(passwords: List<Password>): List<Password> {
        val apiController = apiControllerOrNull() ?: return emptyList()
        if (!isUnlocked()) return emptyList()
        return passwords.decryptPasswords(apiController.csEv1Keychain.value)
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
        val display = label.takeIf { it.isNotBlank() } ?: LOCKED_DISPLAY_NAME
        return PasswordEntry(
            id = id,
            username = LOCKED_USERNAME,
            password = null,
            displayName = display,
            locked = true
        )
    }

    private suspend fun handleExistingEntryForSave(
        request: PasswordSaveRequest,
        apiController: ApiController
    ): PasswordSaveResult? {
        val packageName = request.packageName?.takeIf { it.isNotBlank() }
        val username = request.username?.takeIf { it.isNotBlank() } ?: return null

        val savedPasswords = passwordDatabase.passwordDao.fetchAllPasswordsList()
            .filter { !it.trashed && !it.hidden && !it.isBackupAppKey() }
        val decryptedPasswords = decryptIfUnlocked(savedPasswords)

        val candidates = matcher.candidates(request)

        val exactPasswordMatch = decryptedPasswords.firstOrNull { password ->
            password.username.equals(username, ignoreCase = true) &&
                password.password == request.password &&
                matcher.matches(password, candidates)
        }
        if (exactPasswordMatch != null) {
            if (packageName == null ||
                matcher.hasPackage(exactPasswordMatch, packageName)
            ) {
                debugLog("save duplicate ignored id=${exactPasswordMatch.id}")
                return PasswordSaveResult.DuplicateIgnored
            }

            debugLog("save linking package to existing id=${exactPasswordMatch.id}")
            return updateExistingPassword(
                password = exactPasswordMatch,
                apiController = apiController,
                updatedPassword = exactPasswordMatch.password,
                packageName = packageName
            )
        }

        val matchingUsernameEntries = decryptedPasswords.filter { password ->
            password.username.equals(username, ignoreCase = true) &&
                matcher.matches(password, candidates)
        }

        val existingPassword = when (matchingUsernameEntries.size) {
            0 -> return null
            1 -> matchingUsernameEntries.single()
            else -> {
                val appLinkedMatches = packageName?.let { packageName ->
                    matchingUsernameEntries.filter {
                        matcher.hasPackage(it, packageName)
                    }
                }.orEmpty()
                if (appLinkedMatches.size == 1) {
                    appLinkedMatches.single()
                } else {
                    debugLog("save needs user selection ids=${matchingUsernameEntries.map { it.id }}")
                    return PasswordSaveResult.NeedsUserInteraction(
                        "Multiple matching passwords found; select manually to update one"
                    )
                }
            }
        }

        debugLog("save updating existing id=${existingPassword.id}")
        return updateExistingPassword(
            password = existingPassword,
            apiController = apiController,
            updatedPassword = request.password,
            packageName = packageName
        )
    }

    private suspend fun updateExistingPassword(
        password: Password,
        apiController: ApiController,
        updatedPassword: String,
        packageName: String?
    ): PasswordSaveResult {
        if (apiController.updatePassword(
                password.toUpdatedPassword(apiController, updatedPassword, packageName)
            )
        ) {
            PasswordController.getInstance(appContext).syncPasswords()
            return PasswordSaveResult.Saved
        }

        debugLog("update failed for id=${password.id}; syncing and retrying once")
        PasswordController.getInstance(appContext).syncPasswords()

        val latestPassword = passwordDatabase.passwordDao.fetchAllPasswordsList()
            .firstOrNull { it.id == password.id && !it.trashed && !it.hidden }
            ?.let { decryptIfUnlocked(listOf(it)).firstOrNull() }
            ?: return PasswordSaveResult.Failed("Could not refresh existing password")

        val retried = apiController.updatePassword(
            latestPassword.toUpdatedPassword(apiController, updatedPassword, packageName)
        )
        if (!retried) {
            return PasswordSaveResult.Failed("Could not update existing password")
        }

        PasswordController.getInstance(appContext).syncPasswords()
        return PasswordSaveResult.Saved
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

    private fun Password.toUpdatedPassword(
        apiController: ApiController,
        updatedPassword: String,
        packageName: String?
    ): UpdatedPassword {
        val serverSettings = apiController.serverSettings.value
        val currentKeychain = apiController.csEv1Keychain.value
        val shouldEncrypt = currentKeychain != null && cseType == ApiController.CSE_TYPE
        val hashLength = serverSettings?.passwordSecurityHash ?: 40
        val updatedEdited = if (updatedPassword == password) edited else 0
        val updatedCustomFields = packageName
            ?.takeIf { it.isNotBlank() }
            ?.let { NCPAutofillMetadata.withPackage(customFields, it) }
            ?: customFields

        return if (shouldEncrypt) {
            UpdatedPassword(
                id = id,
                revision = revision,
                password = updatedPassword.encryptValue(currentKeychain.current, currentKeychain),
                label = label.encryptValue(currentKeychain.current, currentKeychain),
                username = username.encryptValue(currentKeychain.current, currentKeychain),
                url = url.encryptValue(currentKeychain.current, currentKeychain),
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
                username = username,
                url = url,
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

    private fun PasswordSaveRequest.toNewPassword(apiController: ApiController): NewPassword {
        val serverSettings = apiController.serverSettings.value
        val currentKeychain = apiController.csEv1Keychain.value
        val shouldEncrypt = currentKeychain != null && serverSettings?.encryptionCse != 0
        val label = saveLabel()
        val url = saveUrl()
        val usernameValue = username.orEmpty()
        val notes = ""
        val customFields = packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
            NCPAutofillMetadata.withPackage("[]", packageName)
        } ?: "[]"
        val hashLength = serverSettings?.passwordSecurityHash ?: 40

        return if (shouldEncrypt) {
            NewPassword(
                password = password.encryptValue(currentKeychain.current, currentKeychain),
                label = label.encryptValue(currentKeychain.current, currentKeychain),
                username = usernameValue.encryptValue(currentKeychain.current, currentKeychain),
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
                username = usernameValue,
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
            ?: packageName?.let { applicationLabel(it) }
            ?: username?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SAVE_LABEL
    }

    private fun PasswordSaveRequest.saveUrl(): String {
        return webDomain?.takeIf { it.isNotBlank() }?.let { domain ->
            if (domain.startsWith("http://") || domain.startsWith("https://")) domain else "https://$domain"
        }
            ?: origin?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun applicationLabel(packageName: String): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            val appInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun debugWarn(message: String, error: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message, error)
        }
    }

    private companion object {
        const val TAG = "NCPPasswordBackend"
        const val DEFAULT_SAVE_LABEL = "Autofill password"
        const val LOCKED_DISPLAY_NAME = "Locked password"
        const val LOCKED_USERNAME = "Unlock to view"
    }
}
