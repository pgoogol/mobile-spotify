package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track

/**
 * Kontrakt cache playlist i utworów.
 *
 * Implementacja w :app zna Room (PlaylistCacheDao); ViewModele i UseCase'y
 * znają tylko ten interfejs — zero Androida w :shared.
 *
 * Liked Songs traktowane są jako specjalna playlista z id = "__liked__".
 * Dla nich snapshot_id zawsze = null, a świeżość weryfikuje się przez TTL.
 *
 * Wszystkie metody są suspend i bezpieczne do wywołania z dowolnego dispatchera —
 * implementacja sama używa Dispatchers.IO wewnętrznie (Room).
 */
interface IPlaylistCacheRepository {

    // ── Lista playlist (nagłówki) ─────────────────────────────────────────

    /**
     * Zwraca cache playlist (bez Liked Songs) lub pustą listę.
     * Posortowane po nazwie (NOCASE).
     */
    suspend fun getCachedPlaylists(): List<Playlist>

    /**
     * Czy cache listy playlist istnieje i nie jest starszy niż ttlMs.
     * Bazuje na MIN(fetched_at) — najstarszy wpis decyduje.
     */
    suspend fun isPlaylistsCacheFresh(ttlMs: Long, now: Long): Boolean

    /**
     * Atomowo nadpisuje listę playlist (usuwa stare, dodaje nowe).
     * Liked Songs (__liked__) ma osobny cykl życia i nie jest dotykany.
     */
    suspend fun cachePlaylists(playlists: List<Playlist>, now: Long)

    // ── Walidacja snapshot_id dla utworów ─────────────────────────────────

    /**
     * Czy cache utworów dla podanej playlisty pasuje do podanego snapshotId.
     * Zwraca false gdy:
     *   - playlist nie ma cache utworów (tracks_snapshot_id = null)
     *   - snapshot się zmienił
     *   - cache jest niespójny (count crossRefs != count utworów po JOIN)
     *
     * Dla Liked Songs (gdzie expectedSnapshotId = null) zawsze zwraca false —
     * używaj zamiast tego isLikedTracksFresh().
     */
    suspend fun areTracksFresh(playlistId: String, expectedSnapshotId: String?): Boolean

    /**
     * Czy cache utworów Liked Songs nie jest starszy niż ttlMs.
     * Liked Songs nie mają snapshot_id, więc walidacja przez TTL.
     */
    suspend fun isLikedTracksFresh(ttlMs: Long, now: Long): Boolean

    // ── Odczyt utworów ────────────────────────────────────────────────────

    /**
     * Zwraca utwory z cache w prawidłowej kolejności lub null gdy:
     *   - brak nagłówka playlisty w cache, lub
     *   - nigdy nie pobierano utworów dla tej playlisty.
     *
     * Pusta lista (nie null) oznacza pustą playlistę z zachowanym cache.
     */
    suspend fun getCachedTracks(playlistId: String): List<Track>?

    // ── Zapis utworów ─────────────────────────────────────────────────────

    /**
     * Zapisuje utwory playlisty (transakcyjnie podmienia cały stan).
     * Aktualizuje tracks_snapshot_id i tracks_fetched_at.
     *
     * @param playlist nagłówek playlisty (potrzebny do FK i Liked Songs)
     * @param tracks utwory w docelowej kolejności
     * @param snapshotId snapshot z momentu fetcha (null dla Liked Songs)
     * @param now timestamp epoch ms
     */
    suspend fun cacheTracks(
        playlist: Playlist,
        tracks: List<Track>,
        snapshotId: String?,
        now: Long
    )

    // ── Inwalidacja ───────────────────────────────────────────────────────

    /**
     * Inwaliduje cache utworów dla jednej playlisty (np. po dodaniu utworu).
     * Usuwa wpisy z playlist_tracks i kasuje tracks_snapshot_id, ale zostawia
     * nagłówek w playlists_cache.
     */
    suspend fun invalidateTracks(playlistId: String)

    /**
     * Inwaliduje cache całej listy playlist (np. po utworzeniu nowej).
     * Wymusza pełny refetch przy następnym getUserPlaylists().
     */
    suspend fun invalidatePlaylistsList()

    // ── Statystyki dla UI ─────────────────────────────────────────────────

    /** Liczba zcache'owanych nagłówków playlist (bez Liked Songs). */
    suspend fun playlistsCount(): Int

    /** Liczba zcache'owanych metadanych utworów. */
    suspend fun tracksCount(): Int

    /**
     * Czas (epoch ms) ostatniego pobrania utworów dla playlisty,
     * lub null gdy nigdy nie pobierano.
     */
    suspend fun getTracksFetchedAt(playlistId: String): Long?

    // ── Czyszczenie ───────────────────────────────────────────────────────

    /** Pełne wyczyszczenie cache — używane przy wylogowaniu. */
    suspend fun clearAll()
}