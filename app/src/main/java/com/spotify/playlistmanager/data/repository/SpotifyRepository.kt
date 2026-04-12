package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.api.SpotifyApiService
import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.repository.CachePolicy
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja ISpotifyRepository.
 *
 * Etap 7: Dodano cache-first dla read operations.
 *  - getUserPlaylists / getPlaylistTracks / getLikedTracks honorują CachePolicy
 *  - Walidacja snapshot_id dla utworów playlisty (tani request ~50B)
 *  - TTL 5 min dla listy playlist, 10 min dla Liked Songs
 *  - Wszystkie sukcesy fetcha aktualizują cache
 *
 * Etap 10: Inwalidacja cache po write operations.
 *  - createPlaylist → invalidatePlaylistsList
 *  - addTracksToPlaylist → invalidateTracks dla danej playlisty
 */
@Singleton
class SpotifyRepository @Inject constructor(
    private val api: SpotifyApiService,
    private val tokenManager: TokenManager,
    private val cache: IPlaylistCacheRepository
) : ISpotifyRepository {

    companion object {
        /** TTL listy playlist — krótkie, bo użytkownik może często zmieniać. */
        private const val PLAYLISTS_TTL_MS = 5 * 60 * 1000L  // 5 min
        /** TTL Liked Songs — dłuższe, lajki dodawane rzadziej niż edytowane playlisty. */
        private const val LIKED_TRACKS_TTL_MS = 10 * 60 * 1000L  // 10 min
        /** ID syntetycznej playlisty Liked Songs — zgodny z GeneratePlaylistUseCase.LIKED_SONGS_ID. */
        private const val LIKED_ID = "__liked__"
    }

    // ════════════════════════════════════════════════════════
    //  Playlisty użytkownika
    // ════════════════════════════════════════════════════════

    /** Backward-compat wariant — używa CACHE_FIRST. */
    override suspend fun getUserPlaylists(): List<Playlist> =
        getUserPlaylists(CachePolicy.CACHE_FIRST)

    override suspend fun getUserPlaylists(policy: CachePolicy): List<Playlist> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            when (policy) {
                CachePolicy.CACHE_ONLY -> return@withContext cache.getCachedPlaylists()

                CachePolicy.CACHE_FIRST -> {
                    if (cache.isPlaylistsCacheFresh(PLAYLISTS_TTL_MS, now)) {
                        val cached = cache.getCachedPlaylists()
                        if (cached.isNotEmpty()) return@withContext cached
                    }
                }

                CachePolicy.NETWORK_ONLY -> { /* fall through */ }
            }

            // Fetch z API — przy CACHE_FIRST fallback na stale cache gdy sieć niedostępna
            try {
                val fresh = fetchAllPlaylistsFromApi()
                cache.cachePlaylists(fresh, now)
                fresh
            } catch (e: Exception) {
                if (policy == CachePolicy.CACHE_FIRST) {
                    val stale = cache.getCachedPlaylists()
                    if (stale.isNotEmpty()) return@withContext stale
                }
                throw e
            }
        }

    // ════════════════════════════════════════════════════════
    //  Utwory z playlisty
    // ════════════════════════════════════════════════════════

    /** Backward-compat wariant — używa CACHE_FIRST. */
    override suspend fun getPlaylistTracks(playlistId: String): List<Track> =
        getPlaylistTracks(playlistId, CachePolicy.CACHE_FIRST)

    override suspend fun getPlaylistTracks(
        playlistId: String,
        policy: CachePolicy
    ): List<Track> = withContext(Dispatchers.IO) {
        if (policy == CachePolicy.CACHE_ONLY) {
            return@withContext cache.getCachedTracks(playlistId) ?: emptyList()
        }

        if (policy == CachePolicy.CACHE_FIRST) {
            // Pobierz aktualny snapshot_id (tani request ~50B) i porównaj z cache
            val currentSnapshot = runCatching {
                api.getPlaylistSnapshot(playlistId).snapshot_id
            }.getOrNull()

            if (currentSnapshot != null && cache.areTracksFresh(playlistId, currentSnapshot)) {
                cache.getCachedTracks(playlistId)?.let { return@withContext it }
            }

            // Snapshot fetch się nie udał (offline) — użyj cache jeśli istnieje
            if (currentSnapshot == null) {
                cache.getCachedTracks(playlistId)?.let { cached ->
                    if (cached.isNotEmpty()) return@withContext cached
                }
            }
        }

        // NETWORK_ONLY albo cache nieaktualny — pełen fetch
        try {
            val items = fetchAllPlaylistItems(playlistId)
            val tracks = items.mapNotNull { it.track?.toDomain() }

            // Zapisz do cache. Snapshot pobieramy ponownie, na wypadek zmiany w trakcie fetcha.
            val finalSnapshot = runCatching {
                api.getPlaylistSnapshot(playlistId).snapshot_id
            }.getOrNull()

            // Potrzebujemy nagłówka do cache'owania. Jeśli nie ma w cache, sprokurujemy minimalny.
            val playlistHeader = cache.getCachedPlaylists().find { it.id == playlistId }
                ?: Playlist(
                    id = playlistId,
                    name = "",       // i tak zostanie nadpisany przy następnym getUserPlaylists
                    description = null,
                    imageUrl = null,
                    trackCount = tracks.size,
                    ownerId = "",
                    snapshotId = finalSnapshot
                )

            cache.cacheTracks(
                playlist = playlistHeader,
                tracks = tracks,
                snapshotId = finalSnapshot,
                now = System.currentTimeMillis()
            )
            tracks
        } catch (e: Exception) {
            if (policy == CachePolicy.CACHE_FIRST) {
                val stale = cache.getCachedTracks(playlistId)
                if (!stale.isNullOrEmpty()) return@withContext stale
            }
            throw e
        }
    }

    /** Backward-compat wariant — używa CACHE_FIRST. */
    override suspend fun getLikedTracks(): List<Track> =
        getLikedTracks(CachePolicy.CACHE_FIRST)

    override suspend fun getLikedTracks(policy: CachePolicy): List<Track> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            when (policy) {
                CachePolicy.CACHE_ONLY ->
                    return@withContext cache.getCachedTracks(LIKED_ID) ?: emptyList()

                CachePolicy.CACHE_FIRST -> {
                    if (cache.isLikedTracksFresh(LIKED_TRACKS_TTL_MS, now)) {
                        cache.getCachedTracks(LIKED_ID)?.let { return@withContext it }
                    }
                }

                CachePolicy.NETWORK_ONLY -> { /* fall through */ }
            }

            // Fetch z API — przy CACHE_FIRST fallback na stale cache gdy sieć niedostępna
            try {
                val fresh = fetchLikedTracksFromApi()

                // Stwórz syntetyczny nagłówek dla Liked Songs
                val likedHeader = Playlist(
                    id = LIKED_ID,
                    name = "❤ Polubione utwory",
                    description = null,
                    imageUrl = null,
                    trackCount = fresh.size,
                    ownerId = tokenManager.getUserId() ?: "",
                    snapshotId = null
                )
                cache.cacheTracks(likedHeader, fresh, snapshotId = null, now = now)
                fresh
            } catch (e: Exception) {
                if (policy == CachePolicy.CACHE_FIRST) {
                    val stale = cache.getCachedTracks(LIKED_ID)
                    if (!stale.isNullOrEmpty()) return@withContext stale
                }
                throw e
            }
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
            val newId = api.createPlaylist(
                userId,
                CreatePlaylistRequest(name = name, description = description, public = false)
            ).id
            // Inwaliduj listę playlist — następne getUserPlaylists pobierze świeże dane
            cache.invalidatePlaylistsList()
            newId
        }

    override suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>) =
        withContext(Dispatchers.IO) {
            uris.chunked(100).forEach { chunk ->
                api.addTracksToPlaylist(playlistId, AddTracksRequest(uris = chunk))
            }
            // Inwaliduj cache utworów tej playlisty
            cache.invalidateTracks(playlistId)
        }

    // ════════════════════════════════════════════════════════
    //  Kolejka odtwarzania
    // ════════════════════════════════════════════════════════

    /**
     * Dodaje utwór do kolejki odtwarzania.
     * Spotify API przyjmuje jeden URI per request.
     * Rzuca wyjątek jeśli brak aktywnego odtwarzacza (HTTP 404).
     */
    override suspend fun addToQueue(uri: String) = withContext(Dispatchers.IO) {
        api.addToQueue(uri)
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

    private suspend fun fetchAllPlaylistsFromApi(): List<Playlist> {
        val all = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        val limit = 50
        do {
            val page = api.getUserPlaylists(limit = limit, offset = offset)
            all.addAll(page.items)
            offset += limit
        } while (page.next != null)
        return all.map { it.toDomain() }
    }

    private suspend fun fetchLikedTracksFromApi(): List<Track> {
        val items = mutableListOf<PlaylistTrackItem>()
        var offset = 0
        do {
            val page = api.getLikedTracks(limit = 50, offset = offset)
            items.addAll(page.items)
            offset += 50
        } while (page.next != null)
        return items.mapNotNull { it.track?.toDomain() }
    }

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
}

// ── Mapery modeli ────────────────────────────────────────────────────────────

private fun SpotifyPlaylist.toDomain() = Playlist(
    id = id,
    name = name,
    description = description,
    imageUrl = images.firstOrNull()?.url,
    trackCount = tracks.total,
    ownerId = owner.id,
    snapshotId = snapshot_id
)

private fun SpotifyTrack.toDomain() = Track(
    id = id,
    title = name,
    artist = artists.joinToString(", ") { it.name },
    album = album.name,
    albumArtUrl = album.images.firstOrNull()?.url,
    durationMs = duration_ms,
    popularity = popularity,
    uri = uri,
    releaseDate = album.release_date,
    previewUrl = preview_url
)