package foundation.e.findmydevice.storage

import android.content.Context
import android.content.SharedPreferences
import foundation.e.findmydevice.data.PasswordCheckResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit
import java.security.MessageDigest

/**
 * PersistentStorage
 *
 * This class provides a simple interface for storing and retrieving data persistently using
 * Android's SharedPreferences.
 **/
class PersistentStorage (context: Context) {

    companion object {
        const val PREFERENCE_STORE = "GeoSmsPrefs"
        const val PASSWORD_KEY = "password"
        const val STATUS_KEY = "status" // Geolocation by SMS is On/off
        const val DATE_BOOLEAN_LIST_KEY = "date_boolean_list"
        const val PASSWORDS_MIRROR_KEY = "passwords_mirror_digest"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCE_STORE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun clear() {
        sharedPreferences.edit { clear() }
    }

    // region Password
    fun savePassword(password: String) {
        sharedPreferences.edit {
            putString(PASSWORD_KEY, password)
        }
    }

    fun getPassword(): String? {
        return sharedPreferences.getString(PASSWORD_KEY, null)
    }
    //endregion

    fun savePasswordsMirrorCode(code: String) {
        sharedPreferences.edit {
            putString(PASSWORDS_MIRROR_KEY, digestOf(code))
        }
    }

    fun passwordsMirrorNeedsUpdate(code: String): Boolean {
        val mirrored = sharedPreferences.getString(PASSWORDS_MIRROR_KEY, null)
        return !mirrored.isNullOrEmpty() && mirrored != digestOf(code)
    }

    private fun digestOf(code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(code.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    // region Status
    fun saveStatus(status: Boolean) {
        sharedPreferences.edit {
            putBoolean(STATUS_KEY, status)
        }
    }

    fun getStatus(): Boolean {
        return sharedPreferences.getBoolean(STATUS_KEY, false)
    }
    //endregion

    // region recursive password test

    // Using Pair<Long,Boolean> structure to save password test results in a list
    fun getCheckedPasswordResultHistory(): List<Pair<Long, Boolean>> {
        val stored = sharedPreferences.getString(DATE_BOOLEAN_LIST_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<PasswordCheckResult>>(stored)
                .map { result -> Pair(result.first, result.second) }
        }.getOrDefault(emptyList())
    }

    fun addCheckedPasswordResult(value: Boolean) {
        val currentList = getCheckedPasswordResultHistory().toMutableList()
        currentList.add(Pair(System.currentTimeMillis(), value))
        val serialized = json.encodeToString(
            currentList.map { (timestamp, result) ->
                PasswordCheckResult(first = timestamp, second = result)
            }
        )
        sharedPreferences.edit { putString(DATE_BOOLEAN_LIST_KEY, serialized) }
    }

    //endregion
}
