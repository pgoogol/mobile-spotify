package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.data.model.TrackAudioFeatures

/**
 * Kontrakt lokalnego cache'u cech audio.
 *
 * Używa domenowego [TrackAudioFeatures] zamiast encji Room [TrackFeaturesCache],
 * dzięki czemu domena pozostaje wolna od zależności Androidowych.
 * Implementacja (TrackFeaturesDao) dokonuje konwersji wewnętrznie.
 */
interface ITrackFeaturesCache {
    suspend fun getFeatures(trackId: String): TrackAudioFeatures?
    suspend fun getFeaturesForIds(ids: List<String>): List<TrackAudioFeatures>
    suspend fun upsert(features: TrackAudioFeatures)
    suspend fun upsertAll(features: List<TrackAudioFeatures>)
    suspend fun count(): Int
    suspend fun clear()
}
