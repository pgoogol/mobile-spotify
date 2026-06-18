package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.data.model.TrackAudioFeatures

/**
 * Kontrakt cache cech audio.
 * Implementacja w :app zna Room; ViewModel/UseCase znają tylko ten interfejs.
 */
interface ITrackFeaturesRepository {
    /** Zwraca cechy dla podanego Spotify Track ID lub null gdy brak. */
    suspend fun getFeatures(spotifyTrackId: String): TrackAudioFeatures?

    /** Zwraca cechy dla wielu ID naraz (mapa id → cechy). */
    suspend fun getFeaturesMap(spotifyTrackIds: List<String>): Map<String, TrackAudioFeatures>

    /** Wstawia/aktualizuje cechy (upsert). */
    suspend fun upsert(features: List<TrackAudioFeatures>)

    /** Liczba rekordów w cache. */
    suspend fun count(): Int

    /** Czyści cały cache. */
    suspend fun clearAll()
}
