package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.data.model.TrackFeaturesCache

/**
 * Kontrakt lokalnego cache'u cech audio.
 * Implementacja (TrackFeaturesDao) zna Room – domain jej nie widzi.
 */
interface ITrackFeaturesCache {
    suspend fun getFeatures(trackId: String): TrackFeaturesCache?
    suspend fun getFeaturesForIds(ids: List<String>): List<TrackFeaturesCache>
    suspend fun upsert(features: TrackFeaturesCache)
    suspend fun upsertAll(features: List<TrackFeaturesCache>)
    suspend fun count(): Int
    suspend fun clear()
}
