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
package com.hegocre.nextcloudpasswords.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.hegocre.nextcloudpasswords.R
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SecureMasterPasswordStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val promptLock = Any()
    private var activePrompt: BiometricPrompt? = null

    private val keyStore: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    val hasSavedMasterPassword: Boolean
        get() = prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV)

    val canUseSecureAuthentication: Boolean
        get() = BiometricManager.from(appContext)
            .canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun createEncryptionCipher(): Cipher? {
        if (!canUseSecureAuthentication) return null
        return runCatching {
            createEncryptionCipherWithKey()
        }.recoverCatching { error ->
            if (error is KeyPermanentlyInvalidatedException) {
                clear()
                createEncryptionCipherWithKey()
            } else {
                throw error
            }
        }.getOrNull()
    }

    private fun createEncryptionCipherWithKey(): Cipher {
        return Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
    }

    private fun createDecryptionCipher(): Cipher? {
        val iv = readRequiredStoredBytes(KEY_IV) ?: return null
        if (!canUseSecureAuthentication) return null
        return runCatching {
            Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
        }.getOrElse {
            clear()
            null
        }
    }

    fun storePassword(
        activity: FragmentActivity,
        masterPassword: String,
        onResult: (StorePasswordResult) -> Unit
    ) {
        startStorePassword(activity, masterPassword, onResult)
    }

    private fun startStorePassword(
        activity: FragmentActivity,
        masterPassword: String,
        onResult: (StorePasswordResult) -> Unit
    ): (() -> Unit)? {
        if (!canUseSecureAuthentication) {
            onResult(StorePasswordResult.BiometricUnavailable)
            return null
        }

        val cipher = createEncryptionCipher()
        if (cipher == null) {
            onResult(StorePasswordResult.EncryptionFailed)
            return null
        }

        return authenticate(
            activity = activity,
            cipher = cipher,
            onAuthenticationError = { onResult(StorePasswordResult.AuthenticationFailed) },
            onAuthenticated = { authenticatedCipher ->
                val result = if (saveMasterPassword(authenticatedCipher, masterPassword)) {
                    StorePasswordResult.Success
                } else {
                    StorePasswordResult.EncryptionFailed
                }
                onResult(result)
            }
        )
    }

    suspend fun storePassword(
        activity: FragmentActivity,
        masterPassword: String
    ): StorePasswordResult = suspendCancellableCoroutine { continuation ->
        val cancelAuthentication = startStorePassword(activity, masterPassword) { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        continuation.invokeOnCancellation {
            cancelAuthentication?.invoke()
        }
    }

    fun getPassword(
        activity: FragmentActivity,
        onResult: (GetPasswordResult) -> Unit
    ) {
        startGetPassword(activity, onResult)
    }

    private fun startGetPassword(
        activity: FragmentActivity,
        onResult: (GetPasswordResult) -> Unit
    ): (() -> Unit)? {
        if (!hasSavedMasterPassword) {
            onResult(GetPasswordResult.NotStored)
            return null
        }

        if (!canUseSecureAuthentication) {
            onResult(GetPasswordResult.BiometricUnavailable)
            return null
        }

        val cipher = createDecryptionCipher()
        if (cipher == null) {
            onResult(GetPasswordResult.DecryptionFailed)
            return null
        }

        return authenticate(
            activity = activity,
            cipher = cipher,
            onAuthenticationError = { onResult(GetPasswordResult.AuthenticationFailed) },
            onAuthenticated = { authenticatedCipher ->
                val password = getMasterPassword(authenticatedCipher)
                val result = if (password != null) {
                    GetPasswordResult.Success(password)
                } else {
                    GetPasswordResult.DecryptionFailed
                }
                onResult(result)
            }
        )
    }

    suspend fun getPassword(activity: FragmentActivity): GetPasswordResult =
        suspendCancellableCoroutine { continuation ->
            val cancelAuthentication = startGetPassword(activity) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                cancelAuthentication?.invoke()
            }
        }

    private fun saveMasterPassword(cipher: Cipher, masterPassword: String): Boolean {
        return runCatching {
            val ciphertext = cipher.doFinal(masterPassword.toByteArray(Charsets.UTF_8))
            prefs.edit {
                putString(KEY_CIPHERTEXT, ciphertext.encodeBase64())
                putString(KEY_IV, cipher.iv.encodeBase64())
            }
        }.isSuccess
    }

    private fun getMasterPassword(cipher: Cipher): String? {
        val ciphertext = readRequiredStoredBytes(KEY_CIPHERTEXT) ?: return null
        return runCatching {
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit { clear() }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return getKey()
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        try {
            keyGenerator.init(keySpec())
        } catch (error: InvalidAlgorithmParameterException) {
            throw BiometricKeyUnavailableException(error)
        }
        return keyGenerator.generateKey()
    }

    private fun getKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun keySpec(): KeyGenParameterSpec {
        return KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                USER_AUTHENTICATION_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .setInvalidatedByBiometricEnrollment(true)
            .build()
    }

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        onAuthenticationError: () -> Unit,
        onAuthenticated: (Cipher) -> Unit
    ): () -> Unit {
        val completed = AtomicBoolean(false)
        lateinit var prompt: BiometricPrompt
        fun finish(action: () -> Unit) {
            if (completed.compareAndSet(false, true)) {
                clearActivePrompt(prompt)
                action()
            }
        }

        prompt = BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    finish(onAuthenticationError)
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher == null) {
                        finish(onAuthenticationError)
                    } else {
                        finish { onAuthenticated(authenticatedCipher) }
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(appContext.getString(R.string.biometric_prompt_title))
            .setDescription(appContext.getString(R.string.biometric_prompt_description))
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()

        setActivePrompt(prompt)
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        return {
            if (completed.compareAndSet(false, true)) {
                clearActivePrompt(prompt)
                prompt.cancelAuthentication()
            }
        }
    }

    private fun setActivePrompt(prompt: BiometricPrompt) {
        synchronized(promptLock) {
            activePrompt?.cancelAuthentication()
            activePrompt = prompt
        }
    }

    private fun clearActivePrompt(prompt: BiometricPrompt) {
        synchronized(promptLock) {
            if (activePrompt === prompt) {
                activePrompt = null
            }
        }
    }

    private fun ByteArray.encodeBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun readRequiredStoredBytes(key: String): ByteArray? {
        val encodedValue = prefs.getString(key, null)
        if (encodedValue == null) {
            clear()
            return null
        }

        return runCatching {
            encodedValue.decodeBase64()
        }.getOrElse {
            clear()
            null
        }
    }

    private fun String.decodeBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/" +
            "${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
        private const val USER_AUTHENTICATION_VALIDITY_SECONDS = 0
        private const val ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        private const val PREFS_NAME = "secure_master_password"
        private const val KEY_ALIAS = "secure_master_password_key"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_IV = "iv"
    }

    private class BiometricKeyUnavailableException(cause: Throwable) : Exception(cause)

    sealed interface StorePasswordResult {
        data object Success : StorePasswordResult
        data object BiometricUnavailable : StorePasswordResult
        data object AuthenticationFailed : StorePasswordResult
        data object EncryptionFailed : StorePasswordResult
    }

    sealed interface GetPasswordResult {
        data class Success(val password: String) : GetPasswordResult
        data object NotStored : GetPasswordResult
        data object BiometricUnavailable : GetPasswordResult
        data object AuthenticationFailed : GetPasswordResult
        data object DecryptionFailed : GetPasswordResult
    }
}
