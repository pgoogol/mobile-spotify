package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.cache.PlaylistCacheDao
import com.spotify.playlistmanager.data.cache.PlaylistEntity
import com.spotify.playlistmanager.data.cache.TrackEntity
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja IPlaylistCacheRepository używająca PlaylistCacheDao (Room).
 *
 * Wszystkie operacje wykonywane są w Dispatchers.IO. Brak wycieku
 * detali Roomowych poza tę klasę — domain dostaje czyste modele Playlist/Track.
 */
@Singleton
class PlaylistCacheRepository @Inject constructor(
    private val dao: PlaylistCacheDao
) : IPlaylistCacheRepository {

    // ── Lista playlist ────────────────────────────────────────────────────

    override suspend fun getCachedPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        dao.getAllPlaylists().map { it.toDomain() }
    }

    override suspend fun isPlaylistsCacheFresh(ttlMs: Long, now: Long): Boolean =
        withContext(Dispatchers.IO) {
            val oldest = dao.getOldestPlaylistFetchTime() ?: return@withContext false
            (now - oldest) < ttlMs
        }

    override suspend fun cachePlaylists(playlists: List<Playlist>, now: Long) =
        withContext(Dispatchers.IO) {
            val entities = playlists.map { PlaylistEntity.fromDomain(it, now) }
            dao.replaceAllPlaylists(entities)
        }

    // ── Walidacja snapshot_id ─────────────────────────────────────────────

    override suspend fun areTracksFresh(
        playlistId: String,
        expectedSnapshotId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        // Brak expected snapshot → nie potrafimy zweryfikować (Liked Songs case)
        if (expectedSnapshotId == null) return@withContext false

        val cachedSnapshot = dao.getTracksSnapshotId(playlistId) ?: return@withContext false
        if (cachedSnapshot != expectedSnapshotId) return@withContext false

        // Sanity check: czy crossRefs są spójne z tracks_cache (sieroty po LRU?)
        val crossRefCount = dao.countCrossRefs(playlistId)
        val joinCount = dao.getTracksForPlaylist(playlistId).size
        crossRefCount == joinCount && crossRefCount > 0
    }

    override suspend fun isLikedTracksFresh(ttlMs: Long, now: Long): Boolean =
        withContext(Dispatchers.IO) {
            val fetchedAt = dao.getTracksFetchedAt(LIKED_ID) ?: return@withContext false
            (now - fetchedAt) < ttlMs
        }

    // ── Odczyt utworów ────────────────────────────────────────────────────

    override suspend fun getCachedTracks(playlistId: String): List<Track>? =
        withContext(Dispatchers.IO) {
            // Brak nagłówka → cache nie istnieje
            dao.getPlaylist(playlistId) ?: return@withContext null
            // tracks_fetched_at == null → nigdy nie pobierano
            dao.getTracksFetchedAt(playlistId) ?: return@withContext null
            dao.getTracksForPlaylist(playlistId).map { it.toDomain() }
        }

    // ── Zapis utworów ─────────────────────────────────────────────────────

    override suspend fun cacheTracks(
        playlist: Playlist,
        tracks: List<Track>,
        snapshotId: String?,
        now: Long
    ) = withContext(Dispatchers.IO) {
        val playlistEntity = PlaylistEntity.fromDomain(playlist, now)
        // mapNotNull pomija lokalne pliki bez id
        val trackEntities = tracks.mapNotNull { TrackEntity.fromDomain(it) }
        dao.replacePlaylistTracks(playlistEntity, trackEntities, snapshotId, now)
    }

    // ── Inwalidacja ───────────────────────────────────────────────────────

    override suspend fun invalidateTracks(playlistId: String) = withContext(Dispatchers.IO) {
        dao.clearPlaylistTracks(playlistId)
        dao.markTracksFetched(playlistId, null, 0L)
    }

    override suspend fun invalidatePlaylistsList() = withContext(Dispatchers.IO) {
        dao.deleteAllExceptLiked()
    }

    // ── Statystyki ────────────────────────────────────────────────────────

    override suspend fun playlistsCount(): Int = withContext(Dispatchers.IO) {
        dao.countPlaylists()
    }

    override suspend fun tracksCount(): Int = withContext(Dispatchers.IO) {
        dao.countTracks()
    }

    override suspend fun getTracksFetchedAt(playlistId: String): Long? =
        withContext(Dispatchers.IO) {
            dao.getTracksFetchedAt(playlistId)
        }

    // ── Czyszczenie ───────────────────────────────────────────────────────

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    companion object {
        private const val LIKED_ID = "__liked__"
    }
}