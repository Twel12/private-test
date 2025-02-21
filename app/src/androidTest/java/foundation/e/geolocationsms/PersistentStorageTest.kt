package foundation.e.geolocationsms

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentStorageTest {

    private lateinit var persistentStorage: PersistentStorage
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        persistentStorage = PersistentStorage(context)
        persistentStorage.clear()
    }

    @After
    fun tearDown() {
        persistentStorage.clear()
    }

    @Test
    fun `savePassword should save the password correctly`() {
        val passwordToSave = "mySecretPassword"
        persistentStorage.savePassword(passwordToSave)
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(passwordToSave, retrievedPassword)
    }

    @Test
    fun `getPassword should return null when no password is saved`() {
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(null, retrievedPassword)
    }

    @Test
    fun  `clear should remove the saved password`() {
        persistentStorage.savePassword("somePassword")
        persistentStorage.clear()
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(null, retrievedPassword)
    }
}