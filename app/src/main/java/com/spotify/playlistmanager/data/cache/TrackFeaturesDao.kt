package com.spotify.playlistmanager.data.cache

import androidx.room.*
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesCache

@Dao
abstract class TrackFeaturesDao : ITrackFeaturesCache {

    // ── Operacje Room (wewnętrzne, na encji) ─────────────────────────────────


    @Query("SELECT * FROM track_features_cache WHERE trackId = :trackId")
    protected abstract suspend fun findById(trackId: String): TrackFeaturesCache?

    @Query("SELECT * FROM track_features_cache WHERE trackId IN (:ids)")
    protected abstract suspend fun findByIds(ids: List<String>): List<TrackFeaturesCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertOne(entity: TrackFeaturesCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(entities: List<TrackFeaturesCache>)

    @Query("SELECT COUNT(*) FROM track_features_cache")
    protected abstract suspend fun countRows(): Int

    @Query("DELETE FROM track_features_cache")
    protected abstract suspend fun deleteAll()

    // ── ITrackFeaturesCache – konwersja domena ↔ Room ───────────────────────

    override suspend fun getFeatures(trackId: String): TrackAudioFeatures? =
        findById(trackId)?.toDomain()

    override suspend fun getFeaturesForIds(ids: List<String>): List<TrackAudioFeatures> =
        findByIds(ids).map { it.toDomain() }

    override suspend fun upsert(features: TrackAudioFeatures) =
        insertOne(TrackFeaturesCache.fromDomain(features))

    override suspend fun upsertAll(features: List<TrackAudioFeatures>) =
        insertAll(features.map { TrackFeaturesCache.fromDomain(it) })

    override suspend fun count(): Int = countRows()

    override suspend fun clear() = deleteAll()
}
