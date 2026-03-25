package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.api.SpotifyApiService
import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
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
/**
 * Implementacja ISpotifyRepository.
 *
 * Zmiany względem poprzedniej wersji:
 * - Usunięto filtrowanie playlist po ownerId — zwracane są wszystkie playlisty
 *   użytkownika, w tym obserwowane (collaborative). Filtr po właścicielu
 *   blokował dostęp do playlist współdzielonych.
 * - Mapery używają TrackAudioFeatures (domenowy) zamiast TrackFeaturesCache (Room).
 * - Usunięto LocalCsvImportHelper (funkcja CSV została usunięta z aplikacji).
 */
@Singleton
class SpotifyRepository @Inject constructor(
    private val api: SpotifyApiService,
    private val tokenManager: TokenManager
) : ISpotifyRepository {

    // ════════════════════════════════════════════════════════
    //  Playlisty użytkownika
    // ════════════════════════════════════════════════════════

    /**
     * Pobiera WSZYSTKIE playlisty użytkownika (automatyczna paginacja).
     *
     * Poprzednia wersja filtrowała po ownerId, co wykluczało playlisty
     * obserwowane/collaborative. Obecna wersja zwraca wszystkie pozycje
     * zwrócone przez endpoint /v1/me/playlists.
     */
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

    // ════════════════════════════════════════════════════════
    //  Utwory z playlisty
    // ════════════════════════════════════════════════════════

    /**
     * Pobiera WSZYSTKIE utwory z playlisty (automatyczna paginacja)
     * i wzbogaca je o audio features z cache lub API.
     */
    override suspend fun getPlaylistTracks(playlistId: String): List<Track> =
        withContext(Dispatchers.IO) {
            val items = fetchAllPlaylistItems(playlistId)
            enrichWithFeatures(items)
        }

    /**
     * Polubione utwory użytkownika.
     */
    override suspend fun getLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
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

    override suspend fun createPlaylist(name: String, description: String): String =
        withContext(Dispatchers.IO) {
            val userId = tokenManager.getUserId()
                ?: api.getCurrentUser()
                    .also { tokenManager.saveUserInfo(it.id, it.display_name) }
                    .id
            val response = api.createPlaylist(
                userId,
                CreatePlaylistRequest(name = name, description = description, public = false)
            )
            response.id
        }

    override suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>) =
        withContext(Dispatchers.IO) {
            // Spotify API akceptuje max 100 URI na jedno żądanie
            uris.chunked(100).forEach { chunk ->
                api.addTracksToPlaylist(playlistId, AddTracksRequest(uris = chunk))
            }
        }

    // ════════════════════════════════════════════════════════
    //  Profil użytkownika
    // ════════════════════════════════════════════════════════

    override suspend fun fetchAndCacheCurrentUser(): SpotifyUser = withContext(Dispatchers.IO) {
        val user = api.getCurrentUser()
        tokenManager.saveUserInfo(user.id, user.display_name)
        user
    }

    override suspend fun getUserProfile(): UserProfile = withContext(Dispatchers.IO) {
        val user = api.getCurrentUser()
        UserProfile(
            id = user.id,
            displayName = user.display_name,
            email = user.email,
            imageUrl = user.images.firstOrNull()?.url,
            country = user.country,
            followers = user.followers?.total ?: 0
        )
    }

    override suspend fun getTopArtists(): List<TopArtist> = withContext(Dispatchers.IO) {
        runCatching {
            api.getTopArtists(limit = 20).items.map { artist ->
                TopArtist(
                    id = artist.id,
                    name = artist.name,
                    imageUrl = artist.images.firstOrNull()?.url,
                    genres = artist.genres,
                    popularity = artist.popularity
                )
            }
        }.getOrElse { emptyList() }
    }

    override suspend fun getLikedTracksCount(): Int = withContext(Dispatchers.IO) {
        runCatching {
            api.getLikedTracks(limit = 1, offset = 0).total
        }.getOrElse { 0 }
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

        val ids = tracks.mapNotNull { it.id }
        val features = if (ids.isNotEmpty()) fetchAudioFeaturesFromApi(ids) else emptyMap()
        return tracks.map { track ->

            track.toDomain(features[track.id])
        }
    }

    private suspend fun fetchAudioFeaturesFromApi(ids: List<String>): Map<String, TrackAudioFeatures> {
        val result = mutableMapOf<String, TrackAudioFeatures>()
        ids.chunked(100).forEach { chunk ->
            runCatching {
                api.getAudioFeatures(chunk.joinToString(","))
                    .audio_features
                    .filterNotNull()
                    .forEach { af -> result[af.id] = af.toDomain() }
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

private fun SpotifyTrack.toDomain(
    features: TrackAudioFeatures? = null
) = Track(
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

private fun AudioFeatures.toDomain() =
    TrackAudioFeatures(
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
