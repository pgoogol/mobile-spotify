package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import org.junit.Assert.*
import org.junit.Test

class TrackFilterTest {

    private fun makeTrack(id: String) = Track(
        id = id, title = "Track $id", artist = "Artist",
        album = "Album", albumArtUrl = null,
        durationMs = 180_000, popularity = 50, uri = "spotify:track:$id"
    )

    private fun makeFeatures(id: String, genres: String, label: String = "Label") =
        TrackAudioFeatures(
            spotifyTrackId = id, bpm = 170f, energy = 70f,
            danceability = 65f, valence = 50f, acousticness = 20f,
            instrumentalness = 5f, loudness = -8f, camelot = "8B",
            musicalKey = "Ab", timeSignature = 4, speechiness = 10f,
            liveness = 15f, genres = genres, label = label, isrc = "US123"
        )

    private val tracks = listOf(
        makeTrack("t1"), makeTrack("t2"), makeTrack("t3"),
        makeTrack("t4"), makeTrack("t5")
    )

    private val featuresMap = mapOf(
        "t1" to makeFeatures("t1", "timba, salsa dura", "Fania Records"),
        "t2" to makeFeatures("t2", "salsa romantica, bolero", "RMM"),
        "t3" to makeFeatures("t3", "reggaeton, latin pop", "Universal"),
        "t4" to makeFeatures("t4", "timba, cuban jazz", "EGREM"),
        "t5" to makeFeatures("t5", "bachata, merengue", "Sony Latin")
    )

    // ── No filters ──────────────────────────────────────────────────────

    @Test
    fun `no filters returns all tracks`() {
        val result = TrackFilter.apply(tracks, featuresMap)
        assertEquals(5, result.size)
    }

    // ── Include genres ──────────────────────────────────────────────────

    @Test
    fun `include genres filters to matching tracks`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            includeGenres = setOf("timba")
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.id == "t1" || it.id == "t4" })
    }

    @Test
    fun `include genres is case insensitive`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            includeGenres = setOf("TIMBA")
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `include genres uses contains matching`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            includeGenres = setOf("salsa")  // matches "salsa dura" and "salsa romantica"
        )
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "t1" })
        assertTrue(result.any { it.id == "t2" })
    }

    @Test
    fun `include multiple genres uses OR logic`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            includeGenres = setOf("timba", "bachata")
        )
        assertEquals(3, result.size) // t1, t4 (timba) + t5 (bachata)
    }

    // ── Exclude genres ──────────────────────────────────────────────────

    @Test
    fun `exclude genres removes matching tracks`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            excludeGenres = setOf("reggaeton")
        )
        assertEquals(4, result.size)
        assertTrue(result.none { it.id == "t3" })
    }

    @Test
    fun `include and exclude combined`() {
        // Include salsa, but exclude romantica
        val result = TrackFilter.apply(
            tracks, featuresMap,
            includeGenres = setOf("salsa"),
            excludeGenres = setOf("romantica")
        )
        assertEquals(1, result.size)
        assertEquals("t1", result[0].id) // "salsa dura" passes, "salsa romantica" excluded
    }

    // ── Labels ──────────────────────────────────────────────────────────

    @Test
    fun `include labels filters correctly`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            includeLabels = setOf("Fania")
        )
        assertEquals(1, result.size)
        assertEquals("t1", result[0].id)
    }

    @Test
    fun `exclude labels filters correctly`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            excludeLabels = setOf("Universal")
        )
        assertEquals(4, result.size)
        assertTrue(result.none { it.id == "t3" })
    }

    @Test
    fun `combined genre and label filters`() {
        val result = TrackFilter.apply(
            tracks, featuresMap,
            includeGenres = setOf("timba"),
            includeLabels = setOf("EGREM")
        )
        // t4 has both timba AND EGREM
        // t1 has timba but NOT EGREM → filtered out by label
        assertEquals(1, result.size)
        assertEquals("t4", result[0].id)
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    fun `tracks without features are excluded by include filter`() {
        val tracksWithExtra = tracks + makeTrack("t6") // no features for t6
        val result = TrackFilter.apply(
            tracksWithExtra, featuresMap,
            includeGenres = setOf("timba")
        )
        assertTrue(result.none { it.id == "t6" })
    }

    @Test
    fun `tracks without features pass through exclude filter`() {
        val tracksWithExtra = tracks + makeTrack("t6")
        val result = TrackFilter.apply(
            tracksWithExtra, featuresMap,
            excludeGenres = setOf("reggaeton")
        )
        // t6 has no features → genres is "" → doesn't contain "reggaeton" → passes
        assertTrue(result.any { it.id == "t6" })
    }

    @Test
    fun `empty tracks list returns empty`() {
        val result = TrackFilter.apply(
            emptyList(), featuresMap,
            includeGenres = setOf("timba")
        )
        assertTrue(result.isEmpty())
    }

    // ── Extract helpers ─────────────────────────────────────────────────

    @Test
    fun `extractUniqueGenres returns sorted distinct genres`() {
        val genres = TrackFilter.extractUniqueGenres(tracks, featuresMap)
        assertTrue(genres.contains("Timba"))
        assertTrue(genres.contains("Bachata"))
        assertTrue(genres.contains("Reggaeton"))
        // Check sorted
        assertEquals(genres, genres.sorted())
        // Check distinct
        assertEquals(genres.size, genres.toSet().size)
    }

    @Test
    fun `extractUniqueLabels returns sorted distinct labels`() {
        val labels = TrackFilter.extractUniqueLabels(tracks, featuresMap)
        assertEquals(5, labels.size)
        assertTrue(labels.contains("Fania Records"))
        assertEquals(labels, labels.sorted())
    }
}
