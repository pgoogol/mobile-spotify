package com.spotify.playlistmanager.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_entries ORDER BY id ASC")
    fun observeAll(): Flow<List<QueueEntity>>

    @Insert
    suspend fun insert(entry: QueueEntity): Long

    @Query("DELETE FROM queue_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM queue_entries")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM queue_entries")
    suspend fun count(): Int
}
