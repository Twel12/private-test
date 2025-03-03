package foundation.e.geolocationsms.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * PersistentStorageTest
 *
 * This class contains unit tests for the PersistentStorage class, ensuring that it correctly stores and retrieves
 * data using SharedPreferences.
 **/
@RunWith(RobolectricTestRunner::class)
class PersistentStorageTest {

    companion object {
        const val NB_HISTORY_TESTS = 10
    }

    private lateinit var persistentStorage: PersistentStorage
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        persistentStorage = PersistentStorage(context)
        persistentStorage.clear()
    }

    @After
    fun tearDown() {
        persistentStorage.clear()
    }

    //region Password
    @Test
    fun `Save password should save the password correctly`() {
        val passwordToSave = "12ABCDEF"
        persistentStorage.savePassword(passwordToSave)
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(passwordToSave, retrievedPassword)
    }

    @Test
    fun `Get password should return null when no password is saved` () {
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(null, retrievedPassword)
    }

    @Test
    fun `Clear should remove the password from storage`() {
        persistentStorage.savePassword("AA11BB22")
        persistentStorage.clear()
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals(null, retrievedPassword)
    }
    @Test
    fun `Save multiple passwords should save the last password correctly`() {
        persistentStorage.savePassword("password1")
        persistentStorage.savePassword("password2")
        persistentStorage.savePassword("password3")
        val retrievedPassword = persistentStorage.getPassword()
        assertEquals("password3", retrievedPassword)
    }
    //endregion

    //region Status
    @Test
    fun `Save status should save the status correctly`() {
        val statusToSave = true
        persistentStorage.saveStatus(statusToSave)
        val retrievedStatus = persistentStorage.getStatus()
        assertEquals(statusToSave, retrievedStatus)
    }

    @Test
    fun `Get status should return false when no status is saved` () {
        val retrievedStatus = persistentStorage.getStatus()
        assertEquals(false, retrievedStatus)
    }

    @Test
    fun `Save multiple status should save the last status correctly`() {
        persistentStorage.saveStatus(false)
        persistentStorage.saveStatus(false)

        //True
        persistentStorage.saveStatus(true)
        var retrievedStatus = persistentStorage.getStatus()
        assertEquals(true, retrievedStatus)

        //False
        persistentStorage.saveStatus(false)
        retrievedStatus = persistentStorage.getStatus()
        assertEquals(false, retrievedStatus)
    }
    //endregion

    //region Check password results
    @Test
    fun `Add check password result should add the result correctly (true case)`() {
        val initialHistory = persistentStorage.getCheckedPasswordResultHistory()
        val initialSize = initialHistory.size
        val result = true
        persistentStorage.addCheckedPasswordResult(result)
        val updatedHistory = persistentStorage.getCheckedPasswordResultHistory()
        assertEquals(initialSize + 1, updatedHistory.size)
        assertEquals(result, updatedHistory.last().second)
    }

    @Test
    fun `Add check password result should add the result correctly (false case)`() {
        val initialHistory = persistentStorage.getCheckedPasswordResultHistory()
        val initialSize = initialHistory.size
        val result = false
        persistentStorage.addCheckedPasswordResult(result)
        val updatedHistory = persistentStorage.getCheckedPasswordResultHistory()
        assertEquals(initialSize + 1, updatedHistory.size)
        assertEquals(result, updatedHistory.last().second)
    }

    @Test
    fun `Get checked password result history returns empty list when no history`() {
        val history = persistentStorage.getCheckedPasswordResultHistory()
        assertEquals(emptyList<Pair<Date, Boolean>>(), history)
    }

    @Test
    fun `Add check password result should add multiple results correctly`() {
        val initialHistory = persistentStorage.getCheckedPasswordResultHistory()
        val initialSize = initialHistory.size
        val result1 = true
        val result2 = false
        val result3 = true
        persistentStorage.addCheckedPasswordResult(result1)
        persistentStorage.addCheckedPasswordResult(result2)
        persistentStorage.addCheckedPasswordResult(result3)
        val updatedHistory = persistentStorage.getCheckedPasswordResultHistory()
        assertEquals(initialSize + 3, updatedHistory.size)
        assertEquals(result1, updatedHistory[updatedHistory.size - 3].second)
        assertEquals(result2, updatedHistory[updatedHistory.size - 2].second)
        assertEquals(result3, updatedHistory.last().second)
    }

    @Test
    fun `Add check password result should add result in chronological order`() {
        val result = true
        val before = System.currentTimeMillis()
        persistentStorage.addCheckedPasswordResult(result)
        val after = System.currentTimeMillis()

        val updatedHistory = persistentStorage.getCheckedPasswordResultHistory()
        assertEquals(1, updatedHistory.size)

        assertEquals(result, updatedHistory.last().second)

        assertTrue(updatedHistory.last().first in before..after)
    }

    @Test
    fun `Add check password result should add multiple results in chronological order`() {
        for (i in 1..NB_HISTORY_TESTS) {
            Thread.sleep(1)
            persistentStorage.addCheckedPasswordResult((i%2)==1)
        }

        val updatedHistory = persistentStorage.getCheckedPasswordResultHistory()
        assertEquals(NB_HISTORY_TESTS, updatedHistory.size)

        for (i in 1 until updatedHistory.size) {
            assertTrue(
                "History not in chronological order at index $i",
                updatedHistory[i].first >= updatedHistory[i - 1].first
            )
            assertTrue(
                "History test result at index $i",
                updatedHistory[i].second == ((i%2)==0) // /!\ Starting at 1 first index is 0
            )
        }
    }
    //endregion
}
