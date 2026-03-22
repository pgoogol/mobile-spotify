package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.data.model.*

/**
 * Kontrakt dostępu do danych Spotify.
 * Implementacja (SpotifyRepository) mieszka w :data i zna Retrofit/Room.
 * ViewModele i use-case'y znają tylko ten interfejs – zero Androida w domain.
 */
interface ISpotifyRepository {
    suspend fun getUserPlaylists(): List<Playlist>
    suspend fun getPlaylistTracks(playlistId: String): List<Track>
    suspend fun getLikedTracks(): List<Track>
    suspend fun createPlaylist(name: String, description: String = ""): String
    suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>)
    suspend fun fetchAndCacheCurrentUser(): SpotifyUser
    suspend fun getUserProfile(): UserProfile
    suspend fun getTopArtists(): List<TopArtist>
    suspend fun getLikedTracksCount(): Int
    suspend fun getCachedFeaturesCount(): Int
}
