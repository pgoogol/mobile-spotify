package com.spotify.playlistmanager.desktop.data.repository

import com.spotify.playlistmanager.data.model.AddTracksRequest
import com.spotify.playlistmanager.data.model.CreatePlaylistRequest
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistTrackItem
import com.spotify.playlistmanager.data.model.SpotifyPlaylist
import com.spotify.playlistmanager.data.model.SpotifyTrack
import com.spotify.playlistmanager.data.model.SpotifyUser
import com.spotify.playlistmanager.data.model.TopArtist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.UserProfile
import com.spotify.playlistmanager.desktop.data.api.SpotifyApiService
import com.spotify.playlistmanager.desktop.data.auth.TokenStore
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementacja [ISpotifyRepository] dla desktopu — sieć bezpośrednio przez
 * Retrofit, bez warstwy cache (Room jest tylko po stronie Androida).
 *
 * Logika domenowa (use-case'y, generator) widzi wyłącznie ten interfejs, więc
 * jest w pełni współdzielona między Androidem a desktopem.
 */
class DesktopSpotifyRepository(
    private val api: SpotifyApiService,
    private val tokenStore: TokenStore,
) : ISpotifyRepository {

    override suspend fun getUserPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val all = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        val limit = 50
        do {
            val page = api.getUserPlaylists(limit = limit, offset = offset)
            all.addAll(page.items)
            offset += limit
        } while (page.next != null)
        all.map { it.toDomain() }
    }

    override suspend fun getPlaylistTracks(playlistId: String): List<Track> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<PlaylistTrackItem>()
            var offset = 0
            do {
                val page = api.getPlaylistTracks(playlistId = playlistId, offset = offset)
                items.addAll(page.items)
                offset += 100
            } while (page.next != null)
            items.mapNotNull { it.track?.toDomain() }
        }

    override suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val items = mutableListOf<PlaylistTrackItem>()
        var offset = 0
        do {
            val page = api.getLikedTracks(limit = 50, offset = offset)
            items.addAll(page.items)
            offset += 50
        } while (page.next != null)
        items.mapNotNull { it.track?.toDomain() }
    }

    override suspend fun createPlaylist(name: String, description: String): String =
        withContext(Dispatchers.IO) {
            val userId = tokenStore.userId
                ?: api.getCurrentUser().also { tokenStore.saveUser(it.id, it.display_name) }.id
            api.createPlaylist(
                userId,
                CreatePlaylistRequest(name = name, description = description, public = false),
            ).id
        }

    override suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>) {
        withContext(Dispatchers.IO) {
            uris.chunked(100).forEach { chunk ->
                api.addTracksToPlaylist(playlistId, AddTracksRequest(uris = chunk))
            }
        }
    }

    override suspend fun replacePlaylistTracks(playlistId: String, uris: List<String>) {
        withContext(Dispatchers.IO) {
            val chunks = uris.chunked(100)
            if (chunks.isEmpty()) {
                api.replacePlaylistTracks(playlistId, AddTracksRequest(uris = emptyList()))
            } else {
                api.replacePlaylistTracks(playlistId, AddTracksRequest(uris = chunks.first()))
                chunks.drop(1).forEach { chunk ->
                    api.addTracksToPlaylist(playlistId, AddTracksRequest(uris = chunk))
                }
            }
        }
    }

    override suspend fun fetchAndCacheCurrentUser(): SpotifyUser = withContext(Dispatchers.IO) {
        val user = api.getCurrentUser()
        tokenStore.saveUser(user.id, user.display_name)
        user
    }

    override suspend fun getUserProfile(): UserProfile = withContext(Dispatchers.IO) {
        val user = api.getCurrentUser()
        UserProfile(
            id = user.id,
            displayName = user.display_name,
            email = user.email,
            imageUrl = user.images?.firstOrNull()?.url,
            country = user.country,
            followers = user.followers?.total ?: 0,
        )
    }

    override suspend fun getTopArtists(): List<TopArtist> = withContext(Dispatchers.IO) {
        runCatching {
            api.getTopArtists(limit = 20).items.map { artist ->
                TopArtist(
                    id = artist.id,
                    name = artist.name,
                    imageUrl = artist.images?.firstOrNull()?.url,
                    genres = artist.genres.orEmpty(),
                    popularity = artist.popularity,
                )
            }
        }.getOrElse { emptyList() }
    }

    override suspend fun getLikedTracksCount(): Int = withContext(Dispatchers.IO) {
        runCatching { api.getLikedTracks(limit = 1, offset = 0).total }.getOrElse { 0 }
    }

    override suspend fun addToQueue(uri: String) {
        withContext(Dispatchers.IO) { api.addToQueue(uri) }
    }
}

// ── Mapery DTO → domena (jak w :app) ──────────────────────────────────────────

private fun SpotifyPlaylist.toDomain() = Playlist(
    id = id,
    name = name,
    description = description,
    imageUrl = images?.firstOrNull()?.url,
    trackCount = tracks.total,
    ownerId = owner.id,
    snapshotId = snapshot_id,
)

private fun SpotifyTrack.toDomain() = Track(
    id = id,
    title = name,
    artist = artists?.joinToString(", ") { it.name } ?: "",
    album = album.name,
    albumArtUrl = album.images?.firstOrNull()?.url,
    durationMs = duration_ms,
    popularity = popularity,
    uri = uri,
    releaseDate = album.release_date,
    previewUrl = preview_url,
)
