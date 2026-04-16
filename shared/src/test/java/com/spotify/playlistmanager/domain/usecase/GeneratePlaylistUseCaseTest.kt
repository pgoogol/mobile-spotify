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
        override suspend fun addToQueue(uri: String) = Unit
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

    /**
     * Helper do tworzenia Track z konkretnymi audio features (BPM/energia)
     * — uzywany w testach pinned z obcej playlisty, gdzie chcemy kontrolowac
     * gdzie pinned trafi w krzywej energii.
     */
    private fun makeTrackFull(
        id: String,
        title: String = "External $id",
        artist: String = "Other Artist"
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = "Other Album",
        albumArtUrl = null,
        durationMs = 200_000,
        popularity = 55,
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

    // ── invoke() with excludeTrackIds ────────────────────────────────────

    @Test
    fun `invoke excludes specified track ids`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5
        )
        val result = useCase(listOf(source), excludeTrackIds = setOf("t1", "t3"))
        assertEquals(3, result.size)
        assertTrue(result.none { it.id == "t1" || it.id == "t3" })
    }

    @Test
    fun `invoke with all tracks excluded returns empty`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5
        )
        val allIds = tracks.mapNotNull { it.id }.toSet()
        val result = useCase(listOf(source), excludeTrackIds = allIds)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke deduplicates across segments within single call`() = runTest {
        // Oba segmenty z tej samej playlisty — nie powinny się powtarzać
        val source1 = PlaylistSource(
            playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
            trackCount = 3
        )
        val source2 = PlaylistSource(
            playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
            trackCount = 3
        )
        val result = useCase(listOf(source1, source2))
        val ids = result.mapNotNull { it.id }
        assertEquals("No duplicate track IDs", ids.size, ids.toSet().size)
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
        assertEquals(3, result.generateResult.tracks.size)
        assertEquals(1, result.generateResult.segments.size)
        assertEquals(1f, result.generateResult.overallMatchPercentage, 0.001f)
    }

    @Test
    fun `generateWithCurves with energy curve matches tracks`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 4,
            energyCurve = EnergyCurve.Rising
        )
        val result = useCase.generateWithCurves(listOf(source))
        assertEquals(4, result.generateResult.tracks.size)
        assertEquals(1, result.generateResult.segments.size)
        assertEquals(4, result.generateResult.segments[0].targetScores.size)
        assertTrue(result.generateResult.overallMatchPercentage in 0f..1f)
    }

    @Test
    fun `generateWithCurves multi-segment with smooth join`() = runTest {
        val sources = listOf(
            PlaylistSource(
                playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
                trackCount = 3, energyCurve = EnergyCurve.Rising
            ),
            PlaylistSource(
                playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
                trackCount = 2, energyCurve = EnergyCurve.Rising
            )
        )
        val result = useCase.generateWithCurves(sources, smoothJoin = true)
        assertEquals(5, result.generateResult.tracks.size)
        assertEquals(2, result.generateResult.segments.size)
    }

    @Test
    fun `generateWithCurves without features maps to score 0`() = runTest {
        val uc = GeneratePlaylistUseCase(
            FakeRepository(tracksByPlaylist = mapOf(playlistId to tracks)),
            FakeFeaturesRepository(emptyMap())
        )
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3, energyCurve = EnergyCurve.Rising
        )
        val result = uc.generateWithCurves(listOf(source))
        assertEquals(3, result.generateResult.tracks.size)
        result.generateResult.segments[0].tracks.forEach {
            assertEquals(0f, it.compositeScore, 0.001f)
        }
    }

    // ── generateWithCurves() with excludeTrackIds ────────────────────────

    @Test
    fun `generateWithCurves excludes specified track ids`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5,
            energyCurve = EnergyCurve.None,
            sortBy = SortOption.POPULARITY
        )
        val result = useCase.generateWithCurves(
            listOf(source),
            excludeTrackIds = setOf("t1", "t3")
        )
        assertEquals(3, result.generateResult.tracks.size)
        assertTrue(result.generateResult.tracks.none { it.id == "t1" || it.id == "t3" })
    }

    @Test
    fun `generateWithCurves returns exhaustion statuses`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            energyCurve = EnergyCurve.None
        )
        val result = useCase.generateWithCurves(listOf(source))
        assertTrue(result.exhaustionStatuses.isNotEmpty())
        val status = result.exhaustionStatuses.first()
        assertEquals(playlistId, status.playlistId)
        assertEquals(5, status.totalTracks)
        assertEquals(3, status.usedTracks)
    }

    @Test
    fun `generateWithCurves detects full exhaustion`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5,
            energyCurve = EnergyCurve.None
        )
        // Wyklucz wszystkie 5 → playlista wyczerpana, 0 utworów
        val result = useCase.generateWithCurves(
            listOf(source),
            excludeTrackIds = setOf("t1", "t2", "t3", "t4", "t5")
        )
        assertTrue(result.generateResult.tracks.isEmpty())
        assertTrue(result.exhaustedPlaylists.isNotEmpty())
        assertTrue(result.exhaustedPlaylists.first().exhausted)
    }

    @Test
    fun `generateWithCurves returns newly generated track ids`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            energyCurve = EnergyCurve.None
        )
        val result = useCase.generateWithCurves(listOf(source))
        assertEquals(3, result.allGeneratedTrackIds.size)
        // Każdy wygenerowany utwór powinien być w allGeneratedTrackIds
        result.generateResult.tracks.forEach { track ->
            assertTrue(track.id in result.allGeneratedTrackIds)
        }
    }

    @Test
    fun `generateWithCurves deduplicates across segments`() = runTest {
        val sources = listOf(
            PlaylistSource(
                playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
                trackCount = 3, energyCurve = EnergyCurve.None
            ),
            PlaylistSource(
                playlist = Playlist(playlistId, "P1", null, null, 5, "owner"),
                trackCount = 3, energyCurve = EnergyCurve.None
            )
        )
        val result = useCase.generateWithCurves(sources)
        val trackIds = result.generateResult.tracks.mapNotNull { it.id }
        assertEquals("No duplicate track IDs across segments",
            trackIds.size, trackIds.toSet().size)
        // Max 5 z jednej playlisty
        assertTrue(trackIds.size <= 5)
    }

    // ── calculateExhaustionStatuses() ────────────────────────────────────

    @Test
    fun `calculateExhaustionStatuses with no used tracks`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3
        )
        val statuses = useCase.calculateExhaustionStatuses(
            listOf(source), usedTrackIds = emptySet()
        )
        assertEquals(1, statuses.size)
        assertEquals(5, statuses[0].totalTracks)
        assertEquals(0, statuses[0].usedTracks)
        assertFalse(statuses[0].exhausted)
    }

    @Test
    fun `calculateExhaustionStatuses with some used tracks`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3
        )
        val statuses = useCase.calculateExhaustionStatuses(
            listOf(source), usedTrackIds = setOf("t1", "t2")
        )
        assertEquals(2, statuses[0].usedTracks)
        assertEquals(3, statuses[0].remainingTracks)
        assertFalse(statuses[0].exhausted)
    }

    @Test
    fun `calculateExhaustionStatuses with all used tracks`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5
        )
        val statuses = useCase.calculateExhaustionStatuses(
            listOf(source), usedTrackIds = setOf("t1", "t2", "t3", "t4", "t5")
        )
        assertTrue(statuses[0].exhausted)
        assertEquals(0, statuses[0].remainingTracks)
        assertEquals(1f, statuses[0].usagePercent, 0.001f)
    }

    @Test
    fun `generateWithCurves includes pinned tracks in result`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            energyCurve = EnergyCurve.Rising,
            pinnedTracks = listOf(
                PinnedTrackInfo("t1", "Track A", "Artysta"),
                PinnedTrackInfo("t4", "Track D", "Artysta")
            )
        )
        val result = useCase.generateWithCurves(listOf(source))
        val resultIds = result.generateResult.tracks.map { it.id }
        assertTrue("t1" in resultIds)
        assertTrue("t4" in resultIds)
        assertEquals(3, result.generateResult.tracks.size)
    }

    @Test
    fun `generateWithCurves pinned tracks ignore excludeTrackIds`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            energyCurve = EnergyCurve.Rising,
            pinnedTracks = listOf(
                PinnedTrackInfo("t1", "Track A", "Artysta")
            )
        )
        val result = useCase.generateWithCurves(
            listOf(source),
            excludeTrackIds = setOf("t1", "t2")
        )
        val resultIds = result.generateResult.tracks.map { it.id }
        assertTrue("t1" in resultIds)
        assertFalse("t2" in resultIds)
    }

    @Test
    fun `generateWithCurves pinned with None curve`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 4,
            energyCurve = EnergyCurve.None,
            sortBy = SortOption.POPULARITY,
            pinnedTracks = listOf(
                PinnedTrackInfo("t4", "Track D", "Artysta")
            )
        )
        val result = useCase.generateWithCurves(listOf(source))
        val resultIds = result.generateResult.tracks.map { it.id }
        assertTrue("t4" in resultIds)
        assertEquals(4, result.generateResult.tracks.size)
    }

    @Test
    fun `generateWithCurves pinned tracks dont cause duplicates`() = runTest {
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 5,
            energyCurve = EnergyCurve.Arc,
            pinnedTracks = listOf(
                PinnedTrackInfo("t1", "Track A", "Artysta"),
                PinnedTrackInfo("t3", "Track C", "Artysta")
            )
        )
        val result = useCase.generateWithCurves(listOf(source))
        val ids = result.generateResult.tracks.map { it.id }
        assertEquals("No duplicates", ids.size, ids.toSet().size)
    }

    // ── Cross-playlist pinned tracks ─────────────────────────────────────

    @Test
    fun `pinned z obcej playlisty trafia do segmentu mimo ze nie ma go w fetchTracks`() = runTest {
        // Segment ma jako zrodlo playlistId="pl-1" (5 trackow t1..t5).
        // Pinned to track "ext-1", ktorego NIE MA w pl-1 — pochodzi z playlisty "pl-other".
        // fullTrack jest wypelniony, sourcePlaylistId = "pl-other".
        // Oczekiwanie: ext-1 trafia do wynikow.
        val externalTrack = makeTrackFull("ext-1", "External Hit")
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            energyCurve = EnergyCurve.None,
            sortBy = SortOption.NONE,
            pinnedTracks = listOf(
                PinnedTrackInfo(
                    id = "ext-1",
                    title = externalTrack.title,
                    artist = externalTrack.artist,
                    albumArtUrl = null,
                    sourcePlaylistId = "pl-other",
                    fullTrack = externalTrack
                )
            )
        )
        val result = useCase.generateWithCurves(listOf(source))
        val resultIds = result.generateResult.tracks.map { it.id }
        assertTrue("ext-1 powinno znalezc sie w wynikach", "ext-1" in resultIds)
        assertEquals(3, result.generateResult.tracks.size)
    }

    @Test
    fun `cross-playlist pinned dziala z krzywa energii`() = runTest {
        // Pinned z obcej playlisty trafia do segmentu z krzywa salsa romantica.
        // ext-2 ma BPM 160 i energy 50 (mid range), wiec powinien dostac slot
        // w polowie krzywej.
        val externalTrack = makeTrackFull("ext-2", "Mid Energy External")
        val featuresWithExternal = featuresMap +
                ("ext-2" to makeFeatures("ext-2", bpm = 160f, energy = 50f, danceability = 60f))
        val uc = GeneratePlaylistUseCase(
            FakeRepository(tracksByPlaylist = mapOf(playlistId to tracks)),
            FakeFeaturesRepository(featuresWithExternal)
        )
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 4,
            energyCurve = EnergyCurve.Rising,
            pinnedTracks = listOf(
                PinnedTrackInfo(
                    id = "ext-2",
                    title = externalTrack.title,
                    artist = externalTrack.artist,
                    sourcePlaylistId = "pl-other",
                    fullTrack = externalTrack
                )
            )
        )
        val result = uc.generateWithCurves(listOf(source))
        val resultIds = result.generateResult.tracks.map { it.id }
        assertTrue("ext-2 powinno trafic do segmentu z krzywa", "ext-2" in resultIds)
        assertEquals(4, result.generateResult.tracks.size)
    }

    @Test
    fun `pinned z obcej playlisty bez fullTrack zostaje pominiety`() = runTest {
        // Defensywny test: gdy ktos podejrzanie zbuduje PinnedTrackInfo z
        // sourcePlaylistId != source.id ALE bez fullTrack — use case nie ma jak
        // odnalezc tracka i powinien po prostu go pominac (bez crashu).
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            energyCurve = EnergyCurve.None,
            sortBy = SortOption.NONE,
            pinnedTracks = listOf(
                PinnedTrackInfo(
                    id = "missing",
                    title = "Ghost",
                    artist = "Nobody",
                    sourcePlaylistId = "pl-other",
                    fullTrack = null  // ← brak danych, nie da sie wstawic
                )
            )
        )
        val result = useCase.generateWithCurves(listOf(source))
        val resultIds = result.generateResult.tracks.map { it.id }
        assertFalse("missing nie powinien znalezc sie w wynikach", "missing" in resultIds)
        // Powinno wziac 3 normalne tracki z pl-1
        assertEquals(3, result.generateResult.tracks.size)
    }

    @Test
    fun `mix local pinned i external pinned w jednym segmencie`() = runTest {
        // t1 jest pinned LOCAL (juz w pl-1).
        // ext-3 jest pinned EXTERNAL (z innej playlisty).
        // Oba powinny znalezc sie w wynikach.
        val externalTrack = makeTrackFull("ext-3", "External Mix")
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 4,
            energyCurve = EnergyCurve.None,
            sortBy = SortOption.NONE,
            pinnedTracks = listOf(
                PinnedTrackInfo("t1", "Track A", "Artysta", sourcePlaylistId = playlistId),
                PinnedTrackInfo(
                    id = "ext-3",
                    title = externalTrack.title,
                    artist = externalTrack.artist,
                    sourcePlaylistId = "pl-other",
                    fullTrack = externalTrack
                )
            )
        )
        val result = useCase.generateWithCurves(listOf(source))
        val resultIds = result.generateResult.tracks.map { it.id }
        assertTrue("t1 (local pinned) powinien byc w wynikach", "t1" in resultIds)
        assertTrue("ext-3 (external pinned) powinien byc w wynikach", "ext-3" in resultIds)
        assertEquals(4, result.generateResult.tracks.size)
    }

    @Test
    fun `external pinned nie wplywa na exhaustion playlisty zrodla`() = runTest {
        // Segment chce 3 tracki, ma 1 external pinned + 2 z pl-1.
        // Status wyczerpania powinien pokazac 2/5 (zuzyte z pl-1), nie 3/5.
        val externalTrack = makeTrackFull("ext-4")
        val source = PlaylistSource(
            playlist = Playlist(playlistId, "Test", null, null, 5, "owner"),
            trackCount = 3,
            energyCurve = EnergyCurve.None,
            sortBy = SortOption.NONE,
            pinnedTracks = listOf(
                PinnedTrackInfo(
                    id = "ext-4",
                    title = externalTrack.title,
                    artist = externalTrack.artist,
                    sourcePlaylistId = "pl-other",
                    fullTrack = externalTrack
                )
            )
        )
        // Po pierwszej generacji exclusion list ma 1 external + 2 local.
        // Druga generacja: status powinien liczyc tylko 2 zuzyte z pl-1.
        val firstResult = useCase.generateWithCurves(listOf(source))
        val firstUsedIds = firstResult.allGeneratedTrackIds

        val secondResult = useCase.generateWithCurves(
            listOf(source),
            excludeTrackIds = firstUsedIds
        )
        // Z 5 trackow w pl-1 minus 2 uzyte = 3 dostepne, plus pinned (zawsze).
        // Wiec drugie wywolanie wciaz zwroci 3 tracki (1 pinned + 2 nowe z pl-1).
        assertEquals(3, secondResult.generateResult.tracks.size)
        assertTrue("ext-4 znow w wynikach (pinned ignoruje exclude)",
            "ext-4" in secondResult.generateResult.tracks.map { it.id })

        // Status wyczerpania pl-1 powinien pokazac 2 zuzyte z 5 (nie 3)
        val pl1Status = secondResult.exhaustionStatuses.find { it.playlistId == playlistId }
        assertNotNull("Status pl-1 powinien istniec", pl1Status)
        assertEquals(5, pl1Status!!.totalTracks)
        assertEquals("Tylko 2 tracki z pl-1 sa zuzyte (ext-4 nie liczy sie)",
            2, pl1Status.usedTracks)
    }
}
