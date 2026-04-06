package com.spotify.playlistmanager.data.cache

import androidx.room.*

/**
 * DAO dla cache playlist i utworów.
 *
 * Kluczowe query to replacePlaylistTracks — transakcyjnie podmienia
 * cały stan utworów playlisty (upsert tracks → clear refs → insert refs →
 * update snapshot). Bez transakcji można by dostać rozjechany cache
 * przy crashu aplikacji w połowie zapisu.
 *
 * Wszystkie metody są suspend — wywoływane z Dispatchers.IO w repozytorium.
 */
@Dao
interface PlaylistCacheDao {

    // ── Playlisty (nagłówki) ─────────────────────────────────────────────

    @Query("SELECT * FROM playlists_cache WHERE id != '__liked__' ORDER BY name COLLATE NOCASE")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists_cache WHERE id = :id LIMIT 1")
    suspend fun getPlaylist(id: String): PlaylistEntity?

    @Query("SELECT snapshot_id FROM playlists_cache WHERE id = :id LIMIT 1")
    suspend fun getSnapshotId(id: String): String?

    @Query("SELECT tracks_snapshot_id FROM playlists_cache WHERE id = :id LIMIT 1")
    suspend fun getTracksSnapshotId(id: String): String?

    @Query("SELECT tracks_fetched_at FROM playlists_cache WHERE id = :id LIMIT 1")
    suspend fun getTracksFetchedAt(id: String): Long?

    @Query("SELECT MIN(fetched_at) FROM playlists_cache WHERE id != '__liked__'")
    suspend fun getOldestPlaylistFetchTime(): Long?

    @Query("SELECT COUNT(*) FROM playlists_cache WHERE id != '__liked__'")
    suspend fun countPlaylists(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    /**
     * Atomowo nadpisuje listę playlist: usuwa te, których nie ma w nowej
     * liście (oprócz __liked__ — Liked Songs ma osobny cykl życia) i wstawia
     * aktualne. Wywoływane po pełnym fetchu /v1/me/playlists.
     */
    @Transaction
    suspend fun replaceAllPlaylists(playlists: List<PlaylistEntity>) {
        deleteAllExceptLiked()
        upsertPlaylists(playlists)
    }

    @Query("DELETE FROM playlists_cache WHERE id != '__liked__'")
    suspend fun deleteAllExceptLiked()

    // ── Utwory (tracks_cache) ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT COUNT(*) FROM tracks_cache")
    suspend fun countTracks(): Int

    // ── Relacja playlist ↔ utwory ────────────────────────────────────────

    /**
     * Pobiera utwory dla playlisty w prawidłowej kolejności (position ASC).
     * INNER JOIN — jeśli utwór został usunięty z tracks_cache (LRU itp.),
     * wiersz jest pomijany. Wtedy cache uznajemy za niekompletny i trzeba
     * zrobić pełny refetch.
     */
    @Query("""
        SELECT t.* FROM tracks_cache t
        INNER JOIN playlist_tracks pt ON pt.track_id = t.id
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC
    """)
    suspend fun getTracksForPlaylist(playlistId: String): List<TrackEntity>

    /**
     * Liczba odwołań (cross-ref) dla playlisty — do wykrycia niekompletnego cache.
     * Jeśli countCrossRefs != getTracksForPlaylist.size, to tracks_cache jest
     * niespójny (sieroty po LRU) i trzeba refetchować.
     */
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun countCrossRefs(playlistId: String): Int

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Insert
    suspend fun insertCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("""
        UPDATE playlists_cache
        SET tracks_snapshot_id = :snapshotId, tracks_fetched_at = :now
        WHERE id = :playlistId
    """)
    suspend fun markTracksFetched(playlistId: String, snapshotId: String?, now: Long)

    /**
     * Atomowa operacja: podmienia komplet utworów playlisty.
     *
     * Kroki w jednej transakcji:
     *   1. Upewnij się, że wiersz w playlists_cache istnieje (FK wymaganie).
     *   2. Upsert wszystkich utworów do tracks_cache.
     *   3. Usuń stare odwołania (playlist_tracks).
     *   4. Wstaw nowe odwołania z position = index.
     *   5. Zaktualizuj tracks_snapshot_id i tracks_fetched_at.
     *
     * @param playlistEntity nagłówek playlisty (dla FK i Liked Songs,
     *                       których nie ma w /v1/me/playlists)
     * @param tracks utwory w kolejności takiej, w jakiej mają trafić do playlisty
     * @param snapshotId aktualny snapshot_id z API (null dla Liked Songs)
     * @param now timestamp operacji (epoch ms)
     */
    @Transaction
    suspend fun replacePlaylistTracks(
        playlistEntity: PlaylistEntity,
        tracks: List<TrackEntity>,
        snapshotId: String?,
        now: Long
    ) {
        // 1. FK wymaga żeby playlist istniała przed insertem cross-refów.
        upsertPlaylist(playlistEntity)
        // 2. Upsert utworów (niezależnie od tego, czy już były w cache).
        if (tracks.isNotEmpty()) upsertTracks(tracks)
        // 3. Wyczyść stare relacje.
        clearPlaylistTracks(playlistEntity.id)
        // 4. Wstaw nowe relacje z zachowaną kolejnością.
        if (tracks.isNotEmpty()) {
            insertCrossRefs(
                tracks.mapIndexed { idx, t ->
                    PlaylistTrackCrossRef(
                        playlistId = playlistEntity.id,
                        trackId = t.id,
                        position = idx
                    )
                }
            )
        }
        // 5. Oznacz kiedy utwory były pobierane i z jakim snapshotem.
        markTracksFetched(playlistEntity.id, snapshotId, now)
    }

    // ── Czyszczenie cache ────────────────────────────────────────────────

    /**
     * Pełne wyczyszczenie cache — używane przy wylogowaniu.
     * CASCADE usuwa playlist_tracks; tracks_cache czyścimy ręcznie.
     */
    @Transaction
    suspend fun clearAll() {
        deleteAllPlaylists()
        deleteAllTracks()
    }

    @Query("DELETE FROM playlists_cache")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM tracks_cache")
    suspend fun deleteAllTracks()
}