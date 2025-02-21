package foundation.e.geolocationsms

import android.content.Context
import android.content.SharedPreferences

class PersistentStorage (context: Context) {

    companion object {
        const val PREFERENCE_STORE = "GeoSmsPrefs"
        const val PASSORD_KEY = "password"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCE_STORE, Context.MODE_PRIVATE)

    fun savePassword(password: String) {
        with(sharedPreferences.edit()) {
            putString(PASSORD_KEY, password)
            apply()
        }
    }

    fun getPassword(): String? {
        return sharedPreferences.getString(PASSORD_KEY, null)
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}