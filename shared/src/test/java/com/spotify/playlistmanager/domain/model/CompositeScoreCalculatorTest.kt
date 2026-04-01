package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import org.junit.Assert.*
import org.junit.Test

class CompositeScoreCalculatorTest {

    private fun makeFeatures(
        bpm: Float = 170f,
        energy: Float = 70f,
        danceability: Float = 65f
    ) = TrackAudioFeatures(
        spotifyTrackId = "test", bpm = bpm, energy = energy,
        danceability = danceability, valence = 50f, acousticness = 20f,
        instrumentalness = 5f, loudness = -8f, camelot = "8B",
        musicalKey = "Ab", timeSignature = 4, speechiness = 10f,
        liveness = 15f, genres = "salsa", label = "Fania", isrc = "US123"
    )

    @Test
    fun `score is weighted sum of normalized components`() {
        val f = makeFeatures(bpm = 175f, energy = 80f, danceability = 60f)
        val bpmNorm = (175f - 130f) / (220f - 130f)
        val energyNorm = 80f / 100f
        val danceNorm = 60f / 100f
        val expected = 0.45f * bpmNorm + 0.35f * energyNorm + 0.20f * danceNorm
        assertEquals(expected, CompositeScoreCalculator.calculate(f), 0.001f)
    }

    @Test
    fun `bpm below range clamps to 0`() {
        assertEquals(0f, CompositeScoreCalculator.normalizeBpm(100f), 0.001f)
        assertEquals(0f, CompositeScoreCalculator.normalizeBpm(130f), 0.001f)
    }

    @Test
    fun `bpm above range clamps to 1`() {
        assertEquals(1f, CompositeScoreCalculator.normalizeBpm(220f), 0.001f)
        assertEquals(1f, CompositeScoreCalculator.normalizeBpm(300f), 0.001f)
    }

    @Test
    fun `bpm mid-range normalizes correctly`() {
        assertEquals(0.5f, CompositeScoreCalculator.normalizeBpm(175f), 0.001f)
    }

    @Test
    fun `score is 0 for all-zero inputs`() {
        assertEquals(0f, CompositeScoreCalculator.calculate(makeFeatures(0f, 0f, 0f)), 0.001f)
    }

    @Test
    fun `score is 1 for max inputs`() {
        assertEquals(1f, CompositeScoreCalculator.calculate(makeFeatures(300f, 100f, 100f)), 0.001f)
    }
}
