package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.cache.TrackFeaturesDao
import com.spotify.playlistmanager.data.cache.TrackFeaturesEntity
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackFeaturesRepository @Inject constructor(
    private val dao: TrackFeaturesDao
) : ITrackFeaturesRepository {

    override suspend fun getFeatures(spotifyTrackId: String): TrackAudioFeatures? =
        withContext(Dispatchers.IO) { dao.getById(spotifyTrackId)?.toDomain() }

    override suspend fun getFeaturesMap(spotifyTrackIds: List<String>): Map<String, TrackAudioFeatures> =
        withContext(Dispatchers.IO) {
            dao.getByIds(spotifyTrackIds).associate { it.spotifyTrackId to it.toDomain() }
        }

    override suspend fun upsert(features: List<TrackAudioFeatures>) =
        withContext(Dispatchers.IO) {
            dao.upsertAll(features.map { TrackFeaturesEntity.fromDomain(it) })
        }

    override suspend fun count(): Int =
        withContext(Dispatchers.IO) { dao.count() }

    override suspend fun clearAll() =
        withContext(Dispatchers.IO) { dao.deleteAll() }
}
