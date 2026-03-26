package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Testy jednostkowe GeneratePlaylistUseCase.
 * Nie wymagają emulatora – czyste Kotlin/JUnit4.
 */
class GeneratePlaylistUseCaseTest {

    // ── Fake repository ──────────────────────────────────────────────────────

    private class FakeRepository(
        private val tracksByPlaylist: Map<String, List<Track>> = emptyMap(),
        private val likedTracks: List<Track> = emptyList()
    ) : ISpotifyRepository {
        override suspend fun getUserPlaylists() = emptyList<Playlist>()
        override suspend fun getPlaylistTracks(playlistId: String) =
            tracksByPlaylist[playlistId] ?: emptyList()
        override suspend fun getLikedTracks() = likedTracks
        override suspend fun createPlaylist(name: String, description: String) = "new-id"
        override suspend fun addTracksToPlaylist(playlistId: String, uris: List<String>) = Unit
        override suspend fun fetchAndCacheCurrentUser() = throw UnsupportedOperationException()
        override suspend fun getUserProfile() = throw UnsupportedOperationException()
        override suspend fun getTopArtists() = emptyList<TopArtist>()
        override suspend fun getLikedTracksCount() = likedTracks.size
    }

    private fun makeTrack(
        id: String,
        title: String,
        popularity: Int = 50,
        durationMs: Int = 180_000
    ) = Track(
        id          = id,
        title       = title,
        artist      = "Artysta",
        album       = "Album",
        albumArtUrl = null,
        durationMs  = durationMs,
        popularity  = popularity,
        uri         = "spotify:track:$id"
    )

    private val playlistId = "pl-1"
    private val tracks = listOf(
        makeTrack("t1", "Track A", popularity = 80),
        makeTrack("t2", "Track B", popularity = 60),
        makeTrack("t3", "Track C", popularity = 90),
        makeTrack("t4", "Track D", popularity = 40),
        makeTrack("t5", "Track E", popularity = 70)
    )

    private lateinit var useCase: GeneratePlaylistUseCase

    @Before
    fun setUp() {
        useCase = GeneratePlaylistUseCase(
            FakeRepository(tracksByPlaylist = mapOf(playlistId to tracks))
        )
    }

    // ── Testy limitu utworów ─────────────────────────────────────────────────

    @Test
    fun `zwraca maksymalnie trackCount utworów`() = runTest {
        val source = PlaylistSource(
            playlist   = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3
        )
        assertEquals(3, useCase(listOf(source)).size)
    }

    @Test
    fun `nie przekracza dostępnej liczby utworów`() = runTest {
        val source = PlaylistSource(
            playlist   = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 100
        )
        assertEquals(5, useCase(listOf(source)).size)
    }

    // ── Testy sortowania ─────────────────────────────────────────────────────

    @Test
    fun `sortowanie po popularności malejąco`() = runTest {
        val source = PlaylistSource(
            playlist   = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5,
            sortBy     = SortOption.POPULARITY
        )
        val result = useCase(listOf(source))
        val popularities = result.map { it.popularity }
        assertEquals(popularities.sortedDescending(), popularities)
    }

    @Test
    fun `sortowanie po czasie trwania rosnąco`() = runTest {
        val durTracks = listOf(
            makeTrack("d1", "Long",   durationMs = 300_000),
            makeTrack("d2", "Short",  durationMs = 120_000),
            makeTrack("d3", "Medium", durationMs = 200_000)
        )
        val repo = FakeRepository(tracksByPlaylist = mapOf("dur" to durTracks))
        val uc   = GeneratePlaylistUseCase(repo)
        val source = PlaylistSource(
            playlist   = Playlist("dur", "Dur", null, null, 3, "owner"),
            trackCount = 3,
            sortBy     = SortOption.DURATION
        )
        val result = uc(listOf(source))
        assertEquals(result.map { it.durationMs }.sorted(), result.map { it.durationMs })
    }

    // ── Testy wielu źródeł ───────────────────────────────────────────────────

    @Test
    fun `łączy utwory z dwóch playlist`() = runTest {
        val pl2Tracks = listOf(makeTrack("x1", "Extra 1"), makeTrack("x2", "Extra 2"))
        val repo = FakeRepository(
            tracksByPlaylist = mapOf(playlistId to tracks, "pl-2" to pl2Tracks)
        )
        val uc = GeneratePlaylistUseCase(repo)
        val sources = listOf(
            PlaylistSource(playlist = Playlist(playlistId, "P1", null, null, 5, "o"), trackCount = 2),
            PlaylistSource(playlist = Playlist("pl-2", "P2", null, null, 2, "o"),     trackCount = 2)
        )
        assertEquals(4, uc(sources).size)
    }

    // ── Testy Liked Songs ────────────────────────────────────────────────────

    @Test
    fun `pobiera polubione gdy id to LIKED_SONGS_ID`() = runTest {
        val liked = listOf(makeTrack("l1", "Liked 1"), makeTrack("l2", "Liked 2"))
        val repo  = FakeRepository(likedTracks = liked)
        val uc    = GeneratePlaylistUseCase(repo)
        val source = PlaylistSource(
            playlist   = Playlist(GeneratePlaylistUseCase.LIKED_SONGS_ID, "Polubione", null, null, 2, ""),
            trackCount = 10
        )
        val result = uc(listOf(source))
        assertEquals(2, result.size)
        assertEquals("l1", result[0].id)
    }

    // ── Testy braku źródła ───────────────────────────────────────────────────

    @Test
    fun `pomija source bez playlisty`() = runTest {
        val source = PlaylistSource(playlist = null, trackCount = 5)
        assertTrue(useCase(listOf(source)).isEmpty())
    }
}
