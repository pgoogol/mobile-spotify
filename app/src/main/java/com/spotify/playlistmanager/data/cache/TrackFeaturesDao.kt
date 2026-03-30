package com.spotify.playlistmanager.data.cache

import androidx.room.*

@Dao
interface TrackFeaturesDao {

    @Query("SELECT * FROM track_features WHERE spotify_track_id = :id LIMIT 1")
    suspend fun getById(id: String): TrackFeaturesEntity?

    @Query("SELECT * FROM track_features WHERE spotify_track_id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrackFeaturesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TrackFeaturesEntity>)

    @Query("SELECT COUNT(*) FROM track_features")
    suspend fun count(): Int

    @Query("DELETE FROM track_features")
    suspend fun deleteAll()

}
