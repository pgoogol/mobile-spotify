package com.spotify.playlistmanager.data.cache

import androidx.room.*
import com.spotify.playlistmanager.data.model.TrackFeaturesCache
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesCache

@Dao
interface TrackFeaturesDao : ITrackFeaturesCache {


    @Query("SELECT * FROM track_features_cache WHERE trackId = :trackId")
    override suspend fun getFeatures(trackId: String): TrackFeaturesCache?

    @Query("SELECT * FROM track_features_cache WHERE trackId IN (:ids)")
    override suspend fun getFeaturesForIds(ids: List<String>): List<TrackFeaturesCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsert(features: TrackFeaturesCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun upsertAll(features: List<TrackFeaturesCache>)

    @Query("SELECT COUNT(*) FROM track_features_cache")
    override suspend fun count(): Int

    @Query("DELETE FROM track_features_cache")
    override suspend fun clear()
}
