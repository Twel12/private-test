package com.hegocre.nextcloudpasswords.databases.passworddatabase

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hegocre.nextcloudpasswords.data.password.Password

@Dao
interface PasswordDatabaseDao {
    @Query("SELECT * FROM passwords")
    fun fetchAllPasswords(): LiveData<List<Password>>

    @Query("SELECT * FROM passwords")
    suspend fun fetchAllPasswordsList(): List<Password>

    @Query("SELECT id FROM passwords")
    suspend fun fetchAllPasswordsId(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM passwords WHERE trashed = 0 AND hidden = 0)")
    suspend fun hasVisiblePasswords(): Boolean

    @Query("SELECT revision FROM passwords WHERE id = :id")
    suspend fun getPasswordRevision(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: Password)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePassword(id: String)

    @Query("DELETE FROM passwords")
    suspend fun deleteDatabase()

    @Transaction
    suspend fun syncWithRemote(remotePasswords: List<Password>) {
        val savedPasswordsSet = fetchAllPasswordsId().toHashSet()
        for (password in remotePasswords) {
            val oldRevision = getPasswordRevision(password.id)
            if (oldRevision == null || oldRevision != password.revision) {
                insertPassword(password)
            }
            savedPasswordsSet.remove(password.id)
        }
        for (id in savedPasswordsSet) {
            deletePassword(id)
        }
    }
}