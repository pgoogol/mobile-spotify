package com.spotify.playlistmanager.desktop.data.api

import com.spotify.playlistmanager.data.model.AddTracksRequest
import com.spotify.playlistmanager.data.model.CreatePlaylistRequest
import com.spotify.playlistmanager.data.model.CreatePlaylistResponse
import com.spotify.playlistmanager.data.model.PlaylistTracksResponse
import com.spotify.playlistmanager.data.model.PlaylistsResponse
import com.spotify.playlistmanager.data.model.SpotifyUser
import com.spotify.playlistmanager.data.model.TopArtistsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface dla Spotify Web API (desktop).
 *
 * Reużywa modeli DTO z modułu :shared (com.spotify.playlistmanager.data.model).
 */
interface SpotifyApiService {

    @GET("v1/me/playlists")
    suspend fun getUserPlaylists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PlaylistsResponse

    @GET("v1/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String =
            "items(track(id,name,artists(id,name),album(id,name,images,release_date),duration_ms,popularity,uri,preview_url)),next,total,offset",
    ): PlaylistTracksResponse

    @GET("v1/me/tracks")
    suspend fun getLikedTracks(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PlaylistTracksResponse

    @GET("v1/me")
    suspend fun getCurrentUser(): SpotifyUser

    @GET("v1/me/top/artists")
    suspend fun getTopArtists(
        @Query("limit") limit: Int = 20,
        @Query("time_range") timeRange: String = "short_term",
    ): TopArtistsResponse

    @POST("v1/users/{user_id}/playlists")
    suspend fun createPlaylist(
        @Path("user_id") userId: String,
        @Body request: CreatePlaylistRequest,
    ): CreatePlaylistResponse

    @POST("v1/playlists/{playlist_id}/tracks")
    suspend fun addTracksToPlaylist(
        @Path("playlist_id") playlistId: String,
        @Body request: AddTracksRequest,
    ): Map<String, String>

    @PUT("v1/playlists/{playlist_id}/tracks")
    suspend fun replacePlaylistTracks(
        @Path("playlist_id") playlistId: String,
        @Body request: AddTracksRequest,
    ): Map<String, String>

    @POST("v1/me/player/queue")
    suspend fun addToQueue(
        @Query("uri") uri: String,
    )
}
