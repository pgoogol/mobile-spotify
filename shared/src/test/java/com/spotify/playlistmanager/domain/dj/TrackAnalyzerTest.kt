package com.spotify.playlistmanager.domain.dj

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.dj.model.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy [TrackAnalyzer] — w szczególności analizy puli pod narzucony styl
 * ([TrackAnalyzer.analyzePoolForStyle]), na której opiera się rozdzielenie
 * dwóch playlist na osobne tandy w trybach Plan / Live (ekran Krok).
 */
class TrackAnalyzerTest {

    private val analyzer = TrackAnalyzer()

    private fun track(id: String) = Track(
        id = id, title = "Track $id", artist = "Artysta $id",
        album = "Album", albumArtUrl = null,
        durationMs = 200_000, popularity = 50, uri = "spotify:track:$id"
    )

    private fun feat(
        id: String,
        genres: String,
        bpm: Float = 170f,
        energy: Float = 70f,
        dance: Float = 60f
    ) = TrackAudioFeatures(
        spotifyTrackId = id, bpm = bpm, energy = energy,
        danceability = dance, valence = 50f, acousticness = 20f,
        instrumentalness = 5f, loudness = -8f, camelot = "8B",
        musicalKey = "Ab", timeSignature = 4, speechiness = 10f,
        liveness = 15f, genres = genres, label = "L", isrc = "US$id"
    )

    // ── analyzePoolForStyle ──────────────────────────────────────────────

    @Test
    fun `analyzePoolForStyle forces one style on all tracks regardless of genres`() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        val features = mapOf(
            "a" to feat("a", genres = "salsa"),
            "b" to feat("b", genres = "bachata"),   // inny wykryty gatunek
            "c" to feat("c", genres = "")            // brak gatunku
        )

        val result = analyzer.analyzePoolForStyle(tracks, features, Style.SALSA)

        // Wszystkie 3 utwory wchodzą do puli i dostają NARZUCONY styl SALSA —
        // łącznie z tym o gatunku "bachata" i tym bez gatunku.
        assertEquals(3, result.size)
        assertTrue(result.all { it.style == Style.SALSA })
        assertEquals(setOf("a", "b", "c"), result.mapNotNull { it.id }.toSet())
    }

    @Test
    fun `analyzePoolForStyle keeps tracks without genres (analyzePool drops them)`() {
        val tracks = listOf(track("a"), track("b"))
        val features = mapOf(
            "a" to feat("a", genres = "salsa"),
            "b" to feat("b", genres = "")  // brak gatunku
        )

        // analyzePool (podział po gatunku) wyrzuca utwór bez rozpoznanego stylu…
        val byStyle = analyzer.analyzePool(tracks, features)
        assertEquals(1, byStyle[Style.SALSA]?.size)

        // …natomiast analyzePoolForStyle zachowuje całą playlistę.
        val forced = analyzer.analyzePoolForStyle(tracks, features, Style.SALSA)
        assertEquals(2, forced.size)
    }

    @Test
    fun `analyzePoolForStyle dedups by track id`() {
        val tracks = listOf(track("a"), track("a"))
        val features = mapOf("a" to feat("a", genres = "salsa"))

        val result = analyzer.analyzePoolForStyle(tracks, features, Style.SALSA)

        assertEquals(1, result.size)
    }

    @Test
    fun `analyzePoolForStyle skips zero-duration and silent tracks`() {
        val tracks = listOf(
            track("ok"),
            track("zero").copy(durationMs = 0),
            track("silent")
        )
        val features = mapOf(
            "ok" to feat("ok", genres = "salsa"),
            "zero" to feat("zero", genres = "salsa"),
            "silent" to feat("silent", genres = "salsa", energy = 0f)
        )

        val result = analyzer.analyzePoolForStyle(tracks, features, Style.SALSA)

        assertEquals(setOf("ok"), result.mapNotNull { it.id }.toSet())
    }

    // ── dominantStyle ────────────────────────────────────────────────────

    @Test
    fun `dominantStyle returns the most common detected style`() {
        val tracks = listOf(track("a"), track("b"), track("c"), track("d"))
        val features = mapOf(
            "a" to feat("a", genres = "salsa"),
            "b" to feat("b", genres = "timba"),    // też SALSA
            "c" to feat("c", genres = "salsa"),
            "d" to feat("d", genres = "bachata")
        )

        assertEquals(Style.SALSA, analyzer.dominantStyle(tracks, features))
    }

    @Test
    fun `dominantStyle is null when no track has a recognizable genre`() {
        val tracks = listOf(track("a"), track("b"))
        val features = mapOf(
            "a" to feat("a", genres = ""),
            "b" to feat("b", genres = "pop")  // nie pasuje do salsy/bachaty
        )

        assertNull(analyzer.dominantStyle(tracks, features))
    }
}
