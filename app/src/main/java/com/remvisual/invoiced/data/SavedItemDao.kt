package com.remvisual.invoiced.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedItemDao {
    @Query("SELECT * FROM saved_items ORDER BY description COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_items ORDER BY description COLLATE NOCASE ASC")
    suspend fun allOnce(): List<SavedItem>

    @Insert
    suspend fun insert(item: SavedItem): Long

    @Update
    suspend fun update(item: SavedItem)

    @Query("SELECT COUNT(*) FROM saved_items WHERE description = :description COLLATE NOCASE")
    suspend fun countByDescription(description: String): Int

    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun delete(id: Long)
}
