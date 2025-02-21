package foundation.e.geolocationsms
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistentStorageTest {

    private lateinit var persistentStorage: PersistentStorage
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        persistentStorage = PersistentStorage(context)
        sharedPreferences = context.getSharedPreferences(PersistentStorage.PREFERENCE_STORE, Context.MODE_PRIVATE)
        persistentStorage.clear()
    }

    @After
    fun tearDown() {
        persistentStorage.clear()
    }

    @Test
    fun savePasswordShouldSaveThePasswordCorrectly() {
        val passwordToSave = "mySecretPassword"
        persistentStorage.savePassword(passwordToSave)
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(passwordToSave, retrievedPassword)
    }

    @Test
    fun getPasswordShouldReturnNullWhenNoPasswordIsSaved() {
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(null, retrievedPassword)
    }

    @Test
    fun clearShouldRemoveTheSavedPassword() {
        persistentStorage.savePassword("somePassword")
        persistentStorage.clear()
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(null, retrievedPassword)
    }
    @Test
    fun saveMultiplePasswordsShouldSaveTheLastPasswordCorrectly() {
        persistentStorage.savePassword("password1")
        persistentStorage.savePassword("password2")
        persistentStorage.savePassword("password3")
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals("password3", retrievedPassword)
    }

    @Test
    fun saveEmptyPasswordShouldSaveTheEmptyPasswordCorrectly() {
        persistentStorage.savePassword("")
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals("", retrievedPassword)
    }
}