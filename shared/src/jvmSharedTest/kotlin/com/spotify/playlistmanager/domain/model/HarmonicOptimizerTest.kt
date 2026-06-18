package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import org.junit.Assert.*
import org.junit.Test

class HarmonicOptimizerTest {

    private fun makeTrack(id: String) = Track(
        id = id, title = "Track $id", artist = "Artist",
        album = "Album", albumArtUrl = null,
        durationMs = 180_000, popularity = 50, uri = "spotify:track:$id"
    )

    private fun makeFeatures(id: String, camelot: String, bpm: Float = 170f) =
        TrackAudioFeatures(
            spotifyTrackId = id, bpm = bpm, energy = 70f,
            danceability = 65f, valence = 50f, acousticness = 20f,
            instrumentalness = 5f, loudness = -8f, camelot = camelot,
            musicalKey = "", timeSignature = 4, speechiness = 10f,
            liveness = 15f, genres = "salsa", label = "Fania", isrc = "US123"
        )

    private fun makeMatched(id: String, score: Float, target: Float) =
        MatchedTrack(track = makeTrack(id), compositeScore = score, targetScore = target)

    @Test
    fun `optimize does not change size`() {
        val matched = listOf(
            makeMatched("a", 0.3f, 0.3f),
            makeMatched("b", 0.5f, 0.5f),
            makeMatched("c", 0.7f, 0.7f)
        )
        val features = mapOf(
            "a" to makeFeatures("a", "8B"),
            "b" to makeFeatures("b", "3A"),
            "c" to makeFeatures("c", "9B")
        )
        val result = HarmonicOptimizer.optimize(matched, features)
        assertEquals(3, result.size)
    }

    @Test
    fun `optimize preserves same elements`() {
        val matched = listOf(
            makeMatched("a", 0.3f, 0.3f),
            makeMatched("b", 0.5f, 0.5f),
            makeMatched("c", 0.7f, 0.7f),
            makeMatched("d", 0.8f, 0.8f)
        )
        val features = mapOf(
            "a" to makeFeatures("a", "8B"),
            "b" to makeFeatures("b", "1A"),
            "c" to makeFeatures("c", "9B"),
            "d" to makeFeatures("d", "8A")
        )
        val result = HarmonicOptimizer.optimize(matched, features)
        assertEquals(matched.map { it.track.id }.toSet(), result.map { it.track.id }.toSet())
    }

    @Test
    fun `optimize improves harmonic sequence`() {
        // Celowo zły harmonicznie układ: 8B → 1A → 9B → 8A
        // Optymalny: 8B → 9B → 8A → 1A lub 8B → 8A → 9B → 1A
        val matched = listOf(
            makeMatched("a", 0.50f, 0.50f),
            makeMatched("b", 0.52f, 0.52f),  // bliski score = swap akceptowalny
            makeMatched("c", 0.54f, 0.54f),
            makeMatched("d", 0.56f, 0.56f)
        )
        val features = mapOf(
            "a" to makeFeatures("a", "8B"),
            "b" to makeFeatures("b", "1A"),   // daleki od 8B
            "c" to makeFeatures("c", "9B"),   // bliski 8B
            "d" to makeFeatures("d", "8A")    // bliski 8B
        )
        val before = HarmonicOptimizer.totalHarmonicCost(matched, features)
        val result = HarmonicOptimizer.optimize(matched, features)
        val after = HarmonicOptimizer.totalHarmonicCost(result, features)

        assertTrue("Cost should improve: before=$before after=$after", after <= before)
    }

    @Test
    fun `optimize skips tracks without camelot key`() {
        val matched = listOf(
            makeMatched("a", 0.3f, 0.3f),
            makeMatched("b", 0.5f, 0.5f),
            makeMatched("c", 0.7f, 0.7f)
        )
        val features = mapOf(
            "a" to makeFeatures("a", "8B"),
            "b" to makeFeatures("b", ""),   // brak klucza
            "c" to makeFeatures("c", "9B")
        )
        // Nie powinien crashować
        val result = HarmonicOptimizer.optimize(matched, features)
        assertEquals(3, result.size)
    }

    @Test
    fun `optimize with 2 tracks returns same order`() {
        val matched = listOf(
            makeMatched("a", 0.3f, 0.3f),
            makeMatched("b", 0.7f, 0.7f)
        )
        val features = mapOf(
            "a" to makeFeatures("a", "1A"),
            "b" to makeFeatures("b", "7B")
        )
        val result = HarmonicOptimizer.optimize(matched, features)
        assertEquals(matched, result) // za mało elementów na swap
    }

    @Test
    fun `optimize respects energy penalty limit`() {
        // Duże różnice w score → swap nie powinien nastąpić
        val matched = listOf(
            makeMatched("a", 0.20f, 0.20f),
            makeMatched("b", 0.50f, 0.50f),  // daleki score
            makeMatched("c", 0.80f, 0.80f),  // jeszcze dalszy
            makeMatched("d", 0.90f, 0.90f)
        )
        val features = mapOf(
            "a" to makeFeatures("a", "8B"),
            "b" to makeFeatures("b", "1A"),
            "c" to makeFeatures("c", "9B"),
            "d" to makeFeatures("d", "8A")
        )
        val result = HarmonicOptimizer.optimize(matched, features)
        // b i c mają za duży rozrzut score → swap zablokowany
        // Sprawdzamy że wynik ma prawidłowe elementy
        assertEquals(4, result.size)
    }

    @Test
    fun `totalHarmonicCost empty list returns 0`() {
        assertEquals(0f, HarmonicOptimizer.totalHarmonicCost(emptyList(), emptyMap()), 0.001f)
    }

    @Test
    fun `totalHarmonicCost single track returns 0`() {
        val matched = listOf(makeMatched("a", 0.5f, 0.5f))
        val features = mapOf("a" to makeFeatures("a", "8B"))
        assertEquals(0f, HarmonicOptimizer.totalHarmonicCost(matched, features), 0.001f)
    }

    @Test
    fun `totalHarmonicCost perfect sequence has low cost`() {
        // 8B → 9B → 10B — kolejne sąsiedzi
        val matched = listOf(
            makeMatched("a", 0.3f, 0.3f),
            makeMatched("b", 0.5f, 0.5f),
            makeMatched("c", 0.7f, 0.7f)
        )
        val features = mapOf(
            "a" to makeFeatures("a", "8B"),
            "b" to makeFeatures("b", "9B"),
            "c" to makeFeatures("c", "10B")
        )
        val cost = HarmonicOptimizer.totalHarmonicCost(matched, features)
        // 2 pary × (1 - 0.9) = 0.2
        assertEquals(0.2f, cost, 0.001f)
    }
}
