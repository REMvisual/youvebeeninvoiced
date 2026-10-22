package com.remvisual.invoiced.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE ASC")
    suspend fun allOnce(): List<Client>

    @Query("SELECT * FROM clients WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Client?

    @Insert
    suspend fun insert(client: Client): Long

    @Update
    suspend fun update(client: Client)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun delete(id: Long)
}
