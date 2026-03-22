package com.spotify.playlistmanager.data.cache

import androidx.room.*
import com.spotify.playlistmanager.data.model.TrackFeaturesCache

@Dao
interface TrackFeaturesDao {

    @Query("SELECT * FROM track_features_cache WHERE trackId = :trackId")
    suspend fun getFeatures(trackId: String): TrackFeaturesCache?

    @Query("SELECT * FROM track_features_cache WHERE trackId IN (:ids)")
    suspend fun getFeaturesForIds(ids: List<String>): List<TrackFeaturesCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(features: TrackFeaturesCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(features: List<TrackFeaturesCache>)

    @Query("SELECT COUNT(*) FROM track_features_cache")
    suspend fun count(): Int

    @Query("DELETE FROM track_features_cache")
    suspend fun clear()
}
