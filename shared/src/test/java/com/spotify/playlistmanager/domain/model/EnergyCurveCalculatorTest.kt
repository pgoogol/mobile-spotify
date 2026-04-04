package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCurveCalculatorTest {

    private fun makeTrack(id: String) = Track(
        id = id, title = "Track $id", artist = "Artist",
        album = "Album", albumArtUrl = null,
        durationMs = 180_000, popularity = 50, uri = "spotify:track:$id"
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

    private val testPool: List<Track>
    private val testFeaturesMap: Map<String, TrackAudioFeatures>

    init {
        val tracks = mutableListOf<Track>()
        val features = mutableMapOf<String, TrackAudioFeatures>()
        for (i in 0 until 20) {
            val id = "t$i"
            val bpm = 130f + (90f * i / 19f)
            tracks.add(makeTrack(id))
            features[id] = makeFeatures(id, bpm = bpm, energy = 50f + i * 2.5f, danceability = 60f)
        }
        testPool = tracks
        testFeaturesMap = features
    }

    @Test
    fun `matchTracks returns correct number of tracks`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRomantica, emptyList(), 8
        )
        assertEquals(8, result.tracks.size)
        assertEquals(8, result.targetScores.size)
    }

    @Test
    fun `matchTracks returns no duplicates`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRapida, emptyList(), 15
        )
        val ids = result.tracks.map { it.track.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `matchTracks handles empty pool`() {
        val result = EnergyCurveCalculator.matchTracks(
            emptyList(), emptyMap(), EnergyCurve.SalsaRomantica, emptyList(), 5
        )
        assertTrue(result.tracks.isEmpty())
    }

    @Test
    fun `matchTracks handles pool smaller than trackCount`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool.take(3), testFeaturesMap, EnergyCurve.SalsaRomantica, emptyList(), 10
        )
        assertEquals(3, result.tracks.size)
    }

    @Test
    fun `match percentage is between 0 and 1`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaClasica, emptyList(), 10
        )
        assertTrue(result.matchPercentage in 0f..1f)
    }

    @Test
    fun `SalsaRomantica matching follows ascending trend`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRomantica, emptyList(), 10
        )
        val scores = result.tracks.map { it.compositeScore }
        assertTrue("Ascending curve should have positive trend", scores.last() - scores.first() > 0)
    }

    @Test
    fun `None curve returns tracks without matching`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.None, emptyList(), 5
        )
        assertEquals(5, result.tracks.size)
        assertTrue(result.targetScores.isEmpty())
        assertEquals(1f, result.matchPercentage, 0.001f)
    }

    @Test
    fun `tracks without features get score 0`() {
        val result = EnergyCurveCalculator.matchTracks(
            listOf(makeTrack("no-features")), emptyMap(), EnergyCurve.SalsaRomantica, emptyList(), 1
        )
        assertEquals(0f, result.tracks[0].compositeScore, 0.001f)
    }

    @Test
    fun `smooth join with null prevLastScore has no effect`() {
        val r1 = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRomantica, emptyList(), 5,
            smoothJoin = true, prevLastScore = null
        )
        val r2 = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRomantica, emptyList(), 5,
            smoothJoin = false, prevLastScore = null
        )
        assertEquals(r1.targetScores, r2.targetScores)
    }

    @Test
    fun `lastScore equals last matched track composite score`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRapida, emptyList(), 5
        )
        assertEquals(result.tracks.last().compositeScore, result.lastScore, 0.001f)
    }

    @Test
    fun `matchTracks with pinned tracks includes all pinned`() {
        val pinned = listOf("t1", "t4")
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRomantica,
            pinnedTrackIds = pinned, trackCount = 5
        )
        val resultIds = result.tracks.map { it.track.id }
        assertTrue("t1" in resultIds)
        assertTrue("t4" in resultIds)
        assertEquals(5, result.tracks.size)
    }

    @Test
    fun `matchTracks pinned tracks dont duplicate`() {
        val pinned = listOf("t1", "t2")
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaClasica,
            pinnedTrackIds = pinned, trackCount = 5
        )
        val ids = result.tracks.map { it.track.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `matchTracks all pinned fills entire segment`() {
        val allIds = testPool.mapNotNull { it.id }
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.SalsaRapida,
            pinnedTrackIds = allIds, trackCount = allIds.size
        )
        assertEquals(allIds.size, result.tracks.size)
        assertEquals(allIds.toSet(), result.tracks.map { it.track.id }.toSet())
    }

    @Test
    fun `matchTracks with None curve pinned first`() {
        val pinned = listOf("t3")
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.None,
            pinnedTrackIds = pinned, trackCount = 3
        )
        assertEquals("t3", result.tracks[0].track.id)
        assertEquals(3, result.tracks.size)
    }
}
