package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.api.SpotifyApiService
import com.spotify.playlistmanager.data.cache.TrackFeaturesDao
import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Odpowiednik spotify_api.py + cache.py.
 *
 * Strategia cache audio features:
 *   1. Sprawdź Room (lokalny cache)
 *   2. Jeśli brak → pobierz z Web API (batch po 100)
 *   3. Zapisz wynik do Room
 */
@Singleton
class SpotifyRepository @Inject constructor(
    private val api: SpotifyApiService,
    private val dao: TrackFeaturesDao,
    private val tokenManager: TokenManager
) {

    // ════════════════════════════════════════════════════════
    //  Playlisty użytkownika
    // ════════════════════════════════════════════════════════

    /**
     * Pobiera WSZYSTKIE playlisty użytkownika (automatyczna paginacja).
     * Zwraca tylko playlisty których właścicielem jest zalogowany user.
     */
    suspend fun getUserPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val userId = tokenManager.getUserId() ?: ""
        val all = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        val limit = 50

        do {
            val page = api.getUserPlaylists(limit = limit, offset = offset)
            all.addAll(page.items)
            offset += limit
        } while (page.next != null)

        all
            .filter { it.owner.id == userId || userId.isEmpty() }
            .map { it.toDomain() }
    }

    // ════════════════════════════════════════════════════════
    //  Utwory z playlisty
    // ════════════════════════════════════════════════════════

    /**
     * Pobiera WSZYSTKIE utwory z playlisty (automatyczna paginacja)
     * i wzbogaca je o audio features z cache lub API.
     */
    suspend fun getPlaylistTracks(playlistId: String): List<Track> =
        withContext(Dispatchers.IO) {
            val items = fetchAllPlaylistItems(playlistId)
            enrichWithFeatures(items)
        }

    /**
     * Polubione utwory użytkownika.
     */
    suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        val items = mutableListOf<PlaylistTrackItem>()
        var offset = 0
        do {
            val page = api.getLikedTracks(limit = 50, offset = offset)
            items.addAll(page.items)
            offset += 50
        } while (page.next != null)
        enrichWithFeatures(items)
    }

    // ════════════════════════════════════════════════════════
    //  Tworzenie playlisty
    // ════════════════════════════════════════════════════════

    suspend fun createPlaylist(name: String, description: String = ""): String =
        withContext(Dispatchers.IO) {
            val userId = tokenManager.getUserId()
                ?: api.getCurrentUser().also { tokenManager.saveUserInfo(it.id, it.display_name) }.id
            val response = api.createPlaylist(
                userId,
                CreatePlaylistRequest(name = name, description = description, public = false)
            )
            response.id
        }

    suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>) =
        withContext(Dispatchers.IO) {
            // Spotify API akceptuje max 100 URI na jedno żądanie
            uris.chunked(100).forEach { chunk ->
                api.addTracksToPlaylist(playlistId, AddTracksRequest(uris = chunk))
            }
        }

    // ════════════════════════════════════════════════════════
    //  Profil użytkownika
    // ════════════════════════════════════════════════════════

    suspend fun fetchAndCacheCurrentUser(): SpotifyUser = withContext(Dispatchers.IO) {
        val user = api.getCurrentUser()
        tokenManager.saveUserInfo(user.id, user.display_name)
        user
    }

    // ════════════════════════════════════════════════════════
    //  Cache cech audio
    // ════════════════════════════════════════════════════════

    suspend fun getCachedFeaturesCount(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }

    // ════════════════════════════════════════════════════════
    //  Prywatne pomocnicze
    // ════════════════════════════════════════════════════════

    private suspend fun fetchAllPlaylistItems(playlistId: String): List<PlaylistTrackItem> {
        val items = mutableListOf<PlaylistTrackItem>()
        var offset = 0
        do {
            val page = api.getPlaylistTracks(playlistId = playlistId, offset = offset)
            items.addAll(page.items)
            offset += 100
        } while (page.next != null)
        return items
    }

    /**
     * Wzbogaca listę elementów playlisty o audio features.
     * Najpierw sprawdza Room, potem odpytuje API w batchach po 100.
     */
    private suspend fun enrichWithFeatures(items: List<PlaylistTrackItem>): List<Track> {
        val tracks = items.mapNotNull { it.track }.filter { it.id != null }

        // 1. Sprawdź co jest w Room cache
        val idsAll = tracks.mapNotNull { it.id }
        val cached = dao.getFeaturesForIds(idsAll).associateBy { it.trackId }

        // 2. Pobierz brakujące z API
        val missing = idsAll.filter { it !in cached }
        val fromApi = if (missing.isNotEmpty()) {
            fetchAudioFeaturesFromApi(missing)
        } else emptyMap()

        // 3. Zapisz nowe do Room
        if (fromApi.isNotEmpty()) {
            dao.upsertAll(fromApi.values.toList())
        }

        // 4. Złóż wynik
        return tracks.map { track ->
            val features = cached[track.id] ?: fromApi[track.id]
            track.toDomain(features)
        }
    }

    private suspend fun fetchAudioFeaturesFromApi(ids: List<String>): Map<String, TrackFeaturesCache> {
        val result = mutableMapOf<String, TrackFeaturesCache>()
        ids.chunked(100).forEach { chunk ->
            runCatching {
                val response = api.getAudioFeatures(chunk.joinToString(","))
                response.audio_features.filterNotNull().forEach { af ->
                    result[af.id] = af.toCache()
                }
            }
        }
        return result
    }
}

// ── Mapery modeli ────────────────────────────────────────────────────────────

private fun SpotifyPlaylist.toDomain() = Playlist(
    id = id,
    name = name,
    description = description,
    imageUrl = images.firstOrNull()?.url,
    trackCount = tracks.total,
    ownerId = owner.id
)

private fun SpotifyTrack.toDomain(features: TrackFeaturesCache? = null) = Track(
    id = id,
    title = name,
    artist = artists.joinToString(", ") { it.name },
    album = album.name,
    albumArtUrl = album.images.firstOrNull()?.url,
    durationMs = duration_ms,
    popularity = popularity,
    uri = uri,
    tempo = features?.tempo,
    energy = features?.energy,
    danceability = features?.danceability,
    valence = features?.valence
)

private fun AudioFeatures.toCache() = TrackFeaturesCache(
    trackId = id,
    tempo = tempo,
    energy = energy,
    danceability = danceability,
    valence = valence,
    acousticness = acousticness,
    instrumentalness = instrumentalness,
    key = key,
    mode = mode
)
