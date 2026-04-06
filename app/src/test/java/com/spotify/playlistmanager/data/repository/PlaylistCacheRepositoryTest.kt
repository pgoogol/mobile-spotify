package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.cache.PlaylistCacheDao
import com.spotify.playlistmanager.data.cache.PlaylistEntity
import com.spotify.playlistmanager.data.cache.PlaylistTrackCrossRef
import com.spotify.playlistmanager.data.cache.TrackEntity
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Testy PlaylistCacheRepository używające in-memory FakeDao.
 * Nie wymagają emulatora ani Robolectric.
 */
class PlaylistCacheRepositoryTest {

    private lateinit var dao: FakePlaylistCacheDao
    private lateinit var repo: PlaylistCacheRepository

    @Before
    fun setUp() {
        dao = FakePlaylistCacheDao()
        repo = PlaylistCacheRepository(dao)
    }

    @Test
    fun `getCachedPlaylists empty when no data`() = runTest {
        assertTrue(repo.getCachedPlaylists().isEmpty())
    }

    @Test
    fun `cachePlaylists then getCachedPlaylists returns same list`() = runTest {
        val playlists = listOf(
            Playlist("p1", "Salsa Mix", null, null, 10, "owner", "snap-1"),
            Playlist("p2", "Bachata Mix", null, null, 5, "owner", "snap-2")
        )
        repo.cachePlaylists(playlists, now = 1000L)

        val cached = repo.getCachedPlaylists()
        assertEquals(2, cached.size)
        // Sortowanie po nazwie NOCASE — Bachata przed Salsa
        assertEquals("Bachata Mix", cached[0].name)
        assertEquals("Salsa Mix", cached[1].name)
        assertEquals("snap-1", cached.find { it.id == "p1" }?.snapshotId)
    }

    @Test
    fun `isPlaylistsCacheFresh true within TTL`() = runTest {
        repo.cachePlaylists(listOf(playlist("p1")), now = 1000L)
        assertTrue(repo.isPlaylistsCacheFresh(ttlMs = 5000L, now = 4000L))
    }

    @Test
    fun `isPlaylistsCacheFresh false after TTL`() = runTest {
        repo.cachePlaylists(listOf(playlist("p1")), now = 1000L)
        assertFalse(repo.isPlaylistsCacheFresh(ttlMs = 5000L, now = 7000L))
    }

    @Test
    fun `isPlaylistsCacheFresh false when empty`() = runTest {
        assertFalse(repo.isPlaylistsCacheFresh(ttlMs = 5000L, now = 1000L))
    }

    @Test
    fun `cacheTracks then getCachedTracks returns same tracks in order`() = runTest {
        val playlist = playlist("p1", snapshotId = "snap-A")
        val tracks = listOf(track("t1", "Song A"), track("t2", "Song B"), track("t3", "Song C"))
        repo.cacheTracks(playlist, tracks, snapshotId = "snap-A", now = 1000L)

        val cached = repo.getCachedTracks("p1")
        assertNotNull(cached)
        assertEquals(3, cached!!.size)
        assertEquals("Song A", cached[0].title)
        assertEquals("Song B", cached[1].title)
        assertEquals("Song C", cached[2].title)
    }

    @Test
    fun `getCachedTracks returns null when never fetched`() = runTest {
        // Nagłówek istnieje, ale tracks_fetched_at jest null
        repo.cachePlaylists(listOf(playlist("p1")), now = 1000L)
        assertNull(repo.getCachedTracks("p1"))
    }

    @Test
    fun `getCachedTracks returns null when no playlist header`() = runTest {
        assertNull(repo.getCachedTracks("nonexistent"))
    }

    @Test
    fun `areTracksFresh true when snapshot matches`() = runTest {
        repo.cacheTracks(
            playlist("p1", snapshotId = "snap-A"),
            listOf(track("t1", "A")),
            snapshotId = "snap-A",
            now = 1000L
        )
        assertTrue(repo.areTracksFresh("p1", "snap-A"))
    }

    @Test
    fun `areTracksFresh false when snapshot differs`() = runTest {
        repo.cacheTracks(
            playlist("p1", snapshotId = "snap-A"),
            listOf(track("t1", "A")),
            snapshotId = "snap-A",
            now = 1000L
        )
        assertFalse(repo.areTracksFresh("p1", "snap-B"))
    }

    @Test
    fun `areTracksFresh false when expected null`() = runTest {
        repo.cacheTracks(
            playlist("p1"),
            listOf(track("t1", "A")),
            snapshotId = null,
            now = 1000L
        )
        assertFalse(repo.areTracksFresh("p1", null))
    }

    @Test
    fun `cacheTracks replaces previous tracks atomically`() = runTest {
        val playlist = playlist("p1", snapshotId = "snap-A")
        repo.cacheTracks(
            playlist,
            listOf(track("t1", "Old A"), track("t2", "Old B")),
            "snap-A",
            1000L
        )
        repo.cacheTracks(playlist, listOf(track("t3", "New C")), "snap-B", 2000L)

        val cached = repo.getCachedTracks("p1")
        assertEquals(1, cached?.size)
        assertEquals("New C", cached?.first()?.title)
    }

    @Test
    fun `cacheTracks skips local files without id`() = runTest {
        val playlist = playlist("p1")
        val tracks = listOf(
            track("t1", "Real"),
            Track(
                id = null, title = "Local file", artist = "x", album = "y",
                albumArtUrl = null, durationMs = 1000, popularity = 0, uri = null
            ),
            track("t2", "Real 2")
        )
        repo.cacheTracks(playlist, tracks, "snap", 1000L)

        val cached = repo.getCachedTracks("p1")
        assertEquals(2, cached?.size)
        assertEquals(listOf("Real", "Real 2"), cached?.map { it.title })
    }

    @Test
    fun `isLikedTracksFresh true within TTL`() = runTest {
        repo.cacheTracks(
            playlist("__liked__"),
            listOf(track("t1", "Liked")),
            snapshotId = null,
            now = 1000L
        )
        assertTrue(repo.isLikedTracksFresh(ttlMs = 5000L, now = 4000L))
    }

    @Test
    fun `isLikedTracksFresh false after TTL`() = runTest {
        repo.cacheTracks(
            playlist("__liked__"),
            listOf(track("t1", "Liked")),
            snapshotId = null,
            now = 1000L
        )
        assertFalse(repo.isLikedTracksFresh(ttlMs = 5000L, now = 7000L))
    }

    @Test
    fun `invalidateTracks clears cache for playlist`() = runTest {
        repo.cacheTracks(
            playlist("p1", snapshotId = "snap-A"),
            listOf(track("t1", "A")),
            snapshotId = "snap-A",
            now = 1000L
        )
        repo.invalidateTracks("p1")
        assertFalse(repo.areTracksFresh("p1", "snap-A"))
    }

    @Test
    fun `clearAll wipes everything`() = runTest {
        repo.cachePlaylists(listOf(playlist("p1"), playlist("p2")), now = 1000L)
        repo.cacheTracks(playlist("p1"), listOf(track("t1", "A")), "snap", 1000L)

        repo.clearAll()

        assertEquals(0, repo.playlistsCount())
        assertEquals(0, repo.tracksCount())
        assertNull(repo.getCachedTracks("p1"))
    }

    @Test
    fun `cachePlaylists preserves liked songs entry`() = runTest {
        // Najpierw zcache'uj Liked Songs
        repo.cacheTracks(
            playlist("__liked__"),
            listOf(track("t1", "Liked")),
            snapshotId = null,
            now = 1000L
        )
        // Potem zaktualizuj listę playlist
        repo.cachePlaylists(listOf(playlist("p1")), now = 2000L)

        // Liked Songs powinien przetrwać
        assertNotNull(repo.getCachedTracks("__liked__"))
        // Nowa playlista też tu jest
        assertEquals(1, repo.playlistsCount())
    }

    // ── Helpery ──────────────────────────────────────────────────────────

    private fun playlist(id: String, snapshotId: String? = null) = Playlist(
        id = id,
        name = "Playlist $id",
        description = null,
        imageUrl = null,
        trackCount = 0,
        ownerId = "owner",
        snapshotId = snapshotId
    )

    private fun track(id: String, title: String) = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumArtUrl = null,
        durationMs = 180_000,
        popularity = 50,
        uri = "spotify:track:$id"
    )
}

/**
 * In-memory fake PlaylistCacheDao do testów jednostkowych.
 * Implementuje minimalny zestaw operacji potrzebny przez repozytorium.
 */
private class FakePlaylistCacheDao : PlaylistCacheDao {
    private val playlists = mutableMapOf<String, PlaylistEntity>()
    private val tracks = mutableMapOf<String, TrackEntity>()
    private val crossRefs = mutableListOf<PlaylistTrackCrossRef>()

    override suspend fun getAllPlaylists(): List<PlaylistEntity> =
        playlists.values
            .filter { it.id != "__liked__" }
            .sortedBy { it.name.lowercase() }

    override suspend fun getPlaylist(id: String): PlaylistEntity? = playlists[id]

    override suspend fun getSnapshotId(id: String): String? = playlists[id]?.snapshotId

    override suspend fun getTracksSnapshotId(id: String): String? = playlists[id]?.tracksSnapshotId

    override suspend fun getTracksFetchedAt(id: String): Long? = playlists[id]?.tracksFetchedAt

    override suspend fun getOldestPlaylistFetchTime(): Long? =
        playlists.values
            .filter { it.id != "__liked__" }
            .minOfOrNull { it.fetchedAt }

    override suspend fun countPlaylists(): Int =
        playlists.values.count { it.id != "__liked__" }

    override suspend fun upsertPlaylists(playlists: List<PlaylistEntity>) {
        playlists.forEach { this.playlists[it.id] = it }
    }

    override suspend fun upsertPlaylist(playlist: PlaylistEntity) {
        playlists[playlist.id] = playlist
    }

    override suspend fun replaceAllPlaylists(playlists: List<PlaylistEntity>) {
        deleteAllExceptLiked()
        upsertPlaylists(playlists)
    }

    override suspend fun deleteAllExceptLiked() {
        playlists.entries.removeAll { it.key != "__liked__" }
    }

    override suspend fun upsertTracks(tracks: List<TrackEntity>) {
        tracks.forEach { this.tracks[it.id] = it }
    }

    override suspend fun countTracks(): Int = tracks.size

    override suspend fun getTracksForPlaylist(playlistId: String): List<TrackEntity> =
        crossRefs
            .filter { it.playlistId == playlistId }
            .sortedBy { it.position }
            .mapNotNull { tracks[it.trackId] }

    override suspend fun countCrossRefs(playlistId: String): Int =
        crossRefs.count { it.playlistId == playlistId }

    override suspend fun clearPlaylistTracks(playlistId: String) {
        crossRefs.removeAll { it.playlistId == playlistId }
    }

    override suspend fun insertCrossRefs(refs: List<PlaylistTrackCrossRef>) {
        crossRefs.addAll(refs)
    }

    override suspend fun markTracksFetched(playlistId: String, snapshotId: String?, now: Long) {
        playlists[playlistId]?.let {
            playlists[playlistId] = it.copy(
                tracksSnapshotId = snapshotId,
                tracksFetchedAt = if (now == 0L) null else now
            )
        }
    }

    override suspend fun replacePlaylistTracks(
        playlistEntity: PlaylistEntity,
        tracks: List<TrackEntity>,
        snapshotId: String?,
        now: Long
    ) {
        upsertPlaylist(playlistEntity)
        if (tracks.isNotEmpty()) upsertTracks(tracks)
        clearPlaylistTracks(playlistEntity.id)
        if (tracks.isNotEmpty()) {
            insertCrossRefs(tracks.mapIndexed { idx, t ->
                PlaylistTrackCrossRef(playlistEntity.id, t.id, idx)
            })
        }
        markTracksFetched(playlistEntity.id, snapshotId, now)
    }

    override suspend fun clearAll() {
        deleteAllPlaylists()
        deleteAllTracks()
    }

    override suspend fun deleteAllPlaylists() {
        playlists.clear()
        crossRefs.clear()
    }

    override suspend fun deleteAllTracks() {
        tracks.clear()
    }
}