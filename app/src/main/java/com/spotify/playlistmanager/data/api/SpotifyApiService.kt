package com.spotify.playlistmanager.data.api

import com.spotify.playlistmanager.data.model.*
import retrofit2.http.*

/**
 * Retrofit interface dla Spotify Web API.
 * Dokumentacja: https://developer.spotify.com/documentation/web-api
 */
interface SpotifyApiService {

    // ── Playlisty ───────────────────────────────────────────────────────────

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PlaylistsResponse

    @GET("v1/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String =
            "items(track(id,name,artists(id,name),album(id,name,images,release_date),duration_ms,popularity,uri,preview_url)),next,total,offset"
    ): PlaylistTracksResponse

    // ── Polubione utwory ────────────────────────────────────────────────────

    @GET("v1/me/tracks")
    suspend fun getLikedTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): PlaylistTracksResponse

    // ── Użytkownik ─────────────────────────────────────────────────────────

    @GET("v1/me")
    suspend fun getCurrentUser(): SpotifyUser

    // ── Top artyści użytkownika ─────────────────────────────────────────────

    @GET("v1/me/top/artists")
    suspend fun getTopArtists(
        @Query("limit")      limit:     Int    = 20,
        @Query("time_range") timeRange: String = "short_term"
    ): TopArtistsResponse

    // ── Tworzenie playlist ─────────────────────────────────────────────────

    @POST("v1/users/{user_id}/playlists")
    suspend fun createPlaylist(
        @Path("user_id") userId: String,
        @Body request: CreatePlaylistRequest
    ): CreatePlaylistResponse

    @POST("v1/playlists/{playlist_id}/tracks")
    suspend fun addTracksToPlaylist(
        @Path("playlist_id") playlistId: String,
        @Body request: AddTracksRequest
    ): Map<String, String>

    // ── Odtwarzacz ─────────────────────────────────────────────────────────

    /**
     * Dodaje utwór do kolejki odtwarzania.
     * Wymaga scope: user-modify-playback-state.
     * Wymaga aktywnego urządzenia odtwarzającego.
     *
     * @param uri URI utworu, np. "spotify:track:4iV5W9uYEdYUVa79Axb7Rh"
     * @see <a href="https://developer.spotify.com/documentation/web-api/reference/add-to-queue">Add to Queue</a>
     */
    @POST("v1/me/player/queue")
    suspend fun addToQueue(
        @Query("uri") uri: String
    )
}
