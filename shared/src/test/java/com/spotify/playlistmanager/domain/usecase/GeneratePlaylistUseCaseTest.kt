package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeneratePlaylistUseCaseTest {

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

    private class FakeFeaturesRepository(
        private val features: Map<String, TrackAudioFeatures> = emptyMap()
    ) : ITrackFeaturesRepository {
        override suspend fun getFeatures(spotifyTrackId: String) = features[spotifyTrackId]
        override suspend fun getFeaturesMap(spotifyTrackIds: List<String>) =
            features.filter { it.key in spotifyTrackIds }
        override suspend fun upsert(features: List<TrackAudioFeatures>) = Unit
        override suspend fun count() = features.size
        override suspend fun clearAll() = Unit
    }

    private fun makeTrack(
        id: String, title: String, popularity: Int = 50, durationMs: Int = 180_000
    ) = Track(
        id = id, title = title, artist = "Artysta", album = "Album",
        albumArtUrl = null, durationMs = durationMs, popularity = popularity,
        uri = "spotify:track:$id"
    )

    private fun makeFeatures(
        id: String, bpm: Float = 170f, energy: Float = 70f, danceability: Float = 65f
    ) = TrackAudioFeatures(
        spotifyTrackId = id, bpm = bpm, energy = energy,
        danceability = danceability, valence = 50f, acousticness = 20f,
        instrumentalness = 5f, loudness = -8f, camelot = "8B",
        musicalKey = "Ab", timeSignature = 4, speechiness = 10f,
        liveness = 15f, genres = "salsa", label = "Fania", isrc = "US123"
    )

    private val playlistId = "pl-1"
    private val tracks = listOf(
        makeTrack("t1", "Track A", popularity = 80),
        makeTrack("t2", "Track B", popularity = 60),
        makeTrack("t3", "Track C", popularity = 90),
        makeTrack("t4", "Track D", popularity = 40),
        makeTrack("t5", "Track E", popularity = 70)
    )

    private val featuresMap = mapOf(
        "t1" to makeFeatures("t1", bpm = 150f, energy = 40f, danceability = 50f),
        "t2" to makeFeatures("t2", bpm = 170f, energy = 60f, danceability = 65f),
        "t3" to makeFeatures("t3", bpm = 190f, energy = 80f, danceability = 75f),
        "t4" to makeFeatures("t4", bpm = 140f, energy = 30f, danceability = 45f),
        "t5" to makeFeatures("t5", bpm = 200f, energy = 90f, danceability = 80f)
    )

    private lateinit var useCase: GeneratePlaylistUseCase

    @Before
    fun setUp() {
        useCase = GeneratePlaylistUseCase(
            FakeRepository(tracksByPlaylist = mapOf(playlistId to tracks)),
            FakeFeaturesRepository(featuresMap)
        )
    }

    // ── Backward compatibility: invoke() ─────────────────────────────────

    @Test
    fun `invoke returns correct track count`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3
        )
        assertEquals(3, useCase(listOf(source)).size)
    }

    @Test
    fun `invoke does not exceed available tracks`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 100
        )
        assertEquals(5, useCase(listOf(source)).size)
    }

    @Test
    fun `invoke sorting by popularity descending`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5,
            sortBy = SortOption.POPULARITY
        )
        val result = useCase(listOf(source))
        assertEquals(result.map { it.popularity }.sortedDescending(), result.map { it.popularity })
    }

    @Test
    fun `invoke combines tracks from two playlists`() = runTest {
        val pl2Tracks = listOf(makeTrack("x1", "Extra 1"), makeTrack("x2", "Extra 2"))
        val repo = FakeRepository(tracksByPlaylist = mapOf(playlistId to tracks, "pl-2" to pl2Tracks))
        val uc = GeneratePlaylistUseCase(repo, FakeFeaturesRepository())
        val sources = listOf(
            PlaylistSource(playlist = Playlist(playlistId, "P1", null, null, 5, "o"), trackCount = 2),
            PlaylistSource(playlist = Playlist("pl-2", "P2", null, null, 2, "o"), trackCount = 2)
        )
        assertEquals(4, uc(sources).size)
    }

    @Test
    fun `invoke fetches liked songs`() = runTest {
        val liked = listOf(makeTrack("l1", "Liked 1"), makeTrack("l2", "Liked 2"))
        val repo = FakeRepository(likedTracks = liked)
        val uc = GeneratePlaylistUseCase(repo, FakeFeaturesRepository())
        val source = PlaylistSource(
            playlist = Playlist(GeneratePlaylistUseCase.LIKED_SONGS_ID, "Polubione", null, null, 2, ""),
            trackCount = 10
        )
        assertEquals(2, uc(listOf(source)).size)
    }

    @Test
    fun `invoke skips source without playlist`() = runTest {
        assertTrue(useCase(listOf(PlaylistSource(playlist = null))).isEmpty())
    }

    // ── generateWithCurves() ─────────────────────────────────────────────

    @Test
    fun `generateWithCurves with None curve uses sorting`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            sortBy = SortOption.POPULARITY,
            energyCurve = EnergyCurve.None
        )
        val result = useCase.generateWithCurves(listOf(source))
        assertEquals(3, result.tracks.size)
        assertEquals(1, result.segments.size)
        assertEquals(1f, result.overallMatchPercentage, 0.001f)
    }

    @Test
    fun `generateWithCurves with energy curve matches tracks`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 4,
            energyCurve = EnergyCurve.SalsaRomantica
        )
        val result = useCase.generateWithCurves(listOf(source))
        assertEquals(4, result.tracks.size)
        assertEquals(1, result.segments.size)
        assertEquals(4, result.segments[0].targetScores.size)
        assertTrue(result.overallMatchPercentage in 0f..1f)
    }

    @Test
    fun `generateWithCurves multi-segment with smooth join`() = runTest {
        val sources = listOf(
            PlaylistSource(
                playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
                trackCount = 3, energyCurve = EnergyCurve.SalsaRomantica
            ),
            PlaylistSource(
                playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
                trackCount = 2, energyCurve = EnergyCurve.SalsaRapida
            )
        )
        val result = useCase.generateWithCurves(sources, smoothJoin = true)
        assertEquals(5, result.tracks.size)
        assertEquals(2, result.segments.size)
    }

    @Test
    fun `generateWithCurves without features maps to score 0`() = runTest {
        val uc = GeneratePlaylistUseCase(
            FakeRepository(tracksByPlaylist = mapOf(playlistId to tracks)),
            FakeFeaturesRepository(emptyMap())
        )
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3, energyCurve = EnergyCurve.SalsaRomantica
        )
        val result = uc.generateWithCurves(listOf(source))
        assertEquals(3, result.tracks.size)
        result.segments[0].tracks.forEach { assertEquals(0f, it.compositeScore, 0.001f) }
    }
}
