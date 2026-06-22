package com.spotify.playlistmanager.desktop.data.repository

import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache cech audio w pamięci dla desktopu.
 *
 * Na razie zasilany ręcznie (docelowo import z CSV — jak na Androidzie).
 * Gdy brak cech dla utworu, generator używa [com.spotify.playlistmanager.domain.model.CompositeScoreCalculator.DEFAULT_SCORE],
 * więc strategie krzywych energii działają najlepiej po wczytaniu cech.
 */
class InMemoryTrackFeaturesRepository : ITrackFeaturesRepository {

    private val map = ConcurrentHashMap<String, TrackAudioFeatures>()

    override suspend fun getFeatures(spotifyTrackId: String): TrackAudioFeatures? =
        map[spotifyTrackId]

    override suspend fun getFeaturesMap(
        spotifyTrackIds: List<String>,
    ): Map<String, TrackAudioFeatures> =
        spotifyTrackIds.mapNotNull { id -> map[id]?.let { id to it } }.toMap()

    override suspend fun upsert(features: List<TrackAudioFeatures>) {
        features.forEach { map[it.spotifyTrackId] = it }
    }

    override suspend fun count(): Int = map.size

    override suspend fun clearAll() {
        map.clear()
    }
}
