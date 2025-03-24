package foundation.e.geolocationsms.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCE_STORE, Context.MODE_PRIVATE)
    private val gson: Gson = Gson()

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    // region Password
    fun savePassword(password: String) {
        with(sharedPreferences.edit()) {
            putString(PASSWORD_KEY, password)
            apply()
        }
    }

    fun getPassword(): String? {
        return sharedPreferences.getString(PASSWORD_KEY, null)
    }
    //endregion

    // region Status
    fun saveStatus(status: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean(STATUS_KEY, status)
            apply()
        }
    }

    fun getStatus(): Boolean {
        return sharedPreferences.getBoolean(STATUS_KEY, false)
    }
    //endregion

    // region recursive password test

    // Using Pair<Long,Boolean> structure to save password test results in a list
    fun getCheckedPasswordResultHistory(): List<Pair<Long, Boolean>> {
        val json = sharedPreferences.getString(DATE_BOOLEAN_LIST_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<Pair<Long, Boolean>>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    fun addCheckedPasswordResult(value: Boolean) {
        val currentList = getCheckedPasswordResultHistory().toMutableList()
        currentList.add(Pair(System.currentTimeMillis(), value))
        val json = gson.toJson(currentList)
        sharedPreferences.edit().putString(DATE_BOOLEAN_LIST_KEY, json).apply()
    }

    //endregion
}
