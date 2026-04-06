package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.data.model.*

/**
 * Kontrakt dostępu do danych Spotify.
 * Implementacja (SpotifyRepository) mieszka w :app i zna Retrofit/Room.
 * ViewModele i use-case'y znają tylko ten interfejs – zero Androida w domain.
 *
 * Metody read (getUserPlaylists, getPlaylistTracks, getLikedTracks) mają dwa
 * warianty: bez parametru (backward compat — używa CACHE_FIRST) i z parametrem
 * CachePolicy. Implementacja domyślna deleguje do wariantu z CACHE_FIRST,
 * dzięki czemu istniejące fake'i w testach kompilują się bez zmian.
 */
interface ISpotifyRepository {

    // ── Read operations z opcjonalną cache policy ─────────────────────────

    suspend fun getUserPlaylists(): List<Playlist>

    suspend fun getUserPlaylists(policy: CachePolicy): List<Playlist> =
        getUserPlaylists()

    suspend fun getPlaylistTracks(playlistId: String): List<Track>

    suspend fun getPlaylistTracks(playlistId: String, policy: CachePolicy): List<Track> =
        getPlaylistTracks(playlistId)

    suspend fun getLikedTracks(): List<Track>

    suspend fun getLikedTracks(policy: CachePolicy): List<Track> =
        getLikedTracks()

    // ── Write operations ──────────────────────────────────────────────────

    suspend fun createPlaylist(name: String, description: String = ""): String
    suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>)

    // ── User profile ──────────────────────────────────────────────────────

    suspend fun fetchAndCacheCurrentUser(): SpotifyUser
    suspend fun getUserProfile(): UserProfile
    suspend fun getTopArtists(): List<TopArtist>
    suspend fun getLikedTracksCount(): Int

    // ── Player ────────────────────────────────────────────────────────────

    /**
     * Dodaje utwór do kolejki odtwarzania.
     * Wymaga scope: user-modify-playback-state.
     * Wymaga aktywnego odtwarzacza — rzuci wyjątek jeśli brak.
     *
     * @param uri URI utworu (np. "spotify:track:xxx")
     */
    suspend fun addToQueue(uri: String)
}