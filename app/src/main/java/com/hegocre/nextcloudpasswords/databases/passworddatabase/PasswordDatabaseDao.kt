package com.hegocre.nextcloudpasswords.databases.passworddatabase

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hegocre.nextcloudpasswords.data.password.Password

@Dao
abstract class PasswordDatabaseDao {
    @Query("SELECT * FROM passwords")
    abstract fun fetchAllPasswords(): LiveData<List<Password>>

    @Query("SELECT * FROM passwords")
    abstract suspend fun fetchAllPasswordsList(): List<Password>

    @Query("SELECT id FROM passwords")
    abstract suspend fun fetchAllPasswordsId(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM passwords WHERE trashed = 0 AND hidden = 0)")
    abstract suspend fun hasVisiblePasswords(): Boolean

    @Query("SELECT revision FROM passwords WHERE id = :id")
    abstract suspend fun getPasswordRevision(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPassword(password: Password)

    @Query("DELETE FROM passwords WHERE id = :id")
    abstract suspend fun deletePassword(id: String)

    @Query("DELETE FROM passwords")
    abstract suspend fun deleteDatabase()

    @Transaction
    open suspend fun syncWithRemote(remotePasswords: List<Password>) {
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
