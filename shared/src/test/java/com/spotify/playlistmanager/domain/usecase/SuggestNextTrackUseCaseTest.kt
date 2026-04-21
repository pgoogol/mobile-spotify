package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.NextTrackTarget
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestNextTrackUseCaseTest {

    // ── Test fixtures ────────────────────────────────────────────────────

    private fun track(id: String) = Track(
        id = id, title = "Track $id", artist = "A",
        album = "Album", albumArtUrl = null,
        durationMs = 180_000, popularity = 50, uri = "spotify:track:$id"
    )

    private fun feat(
        id: String,
        bpm: Float = 170f,
        energy: Float = 70f,
        dance: Float = 60f,
        valence: Float = 50f,
        acoustic: Float = 20f,
        camelot: String = "8B"
    ) = TrackAudioFeatures(
        spotifyTrackId = id, bpm = bpm, energy = energy,
        danceability = dance, valence = valence, acousticness = acoustic,
        instrumentalness = 5f, loudness = -8f, camelot = camelot,
        musicalKey = "Ab", timeSignature = 4, speechiness = 10f,
        liveness = 15f, genres = "salsa", label = "Fania", isrc = "US$id"
    )

    private class FakeFeaturesRepo(
        private val data: Map<String, TrackAudioFeatures>
    ) : ITrackFeaturesRepository {
        override suspend fun getFeatures(spotifyTrackId: String) = data[spotifyTrackId]
        override suspend fun getFeaturesMap(spotifyTrackIds: List<String>) =
            spotifyTrackIds.mapNotNull { id -> data[id]?.let { id to it } }.toMap()
        override suspend fun upsert(features: List<TrackAudioFeatures>) = Unit
        override suspend fun count() = data.size
        override suspend fun clearAll() = Unit
    }

    private fun usecase(features: Map<String, TrackAudioFeatures>) =
        SuggestNextTrackUseCase(FakeFeaturesRepo(features))

    // ── Podstawowe sanity checks ─────────────────────────────────────────

    @Test
    fun `suggest returns at most k candidates`() = runBlocking {
        val pool = (0 until 20).map { track("t$it") }
        val features = pool.associate {
            it.id!! to feat(it.id!!, bpm = 150f + it.id!!.last().code)
        }
        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = emptySet(),
            lastPickedTrack = null,
            target = NextTrackTarget.Peak,
            k = 5
        )
        assertEquals(5, result.candidates.size)
    }

    @Test
    fun `suggest excludes already picked tracks (hard dedup)`() = runBlocking {
        val pool = (0 until 10).map { track("t$it") }
        val features = pool.associate { it.id!! to feat(it.id!!) }
        val picked = setOf("t0", "t1", "t2")

        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = picked,
            lastPickedTrack = null,
            target = NextTrackTarget.Peak,
            k = 10
        )

        assertEquals(7, result.candidates.size)
        val ids = result.candidates.map { it.track.id }.toSet()
        assertTrue(ids.intersect(picked).isEmpty())
    }

    @Test
    fun `suggest returns empty when pool empty`() = runBlocking {
        val result = usecase(emptyMap()).suggest(
            pool = emptyList(),
            alreadyPickedIds = emptySet(),
            lastPickedTrack = null,
            target = NextTrackTarget.Peak
        )
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `suggest returns empty when all tracks already picked`() = runBlocking {
        val pool = (0 until 5).map { track("t$it") }
        val features = pool.associate { it.id!! to feat(it.id!!) }
        val allIds = pool.mapNotNull { it.id }.toSet()

        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = allIds,
            lastPickedTrack = null,
            target = NextTrackTarget.Hold
        )
        assertTrue(result.candidates.isEmpty())
    }

    // ── resolveTarget ────────────────────────────────────────────────────

    @Test
    fun `Peak resolves to 0_9 on DANCE axis regardless of history`() {
        val uc = usecase(emptyMap())
        val (score, axis) = uc.resolveTarget(
            target = NextTrackTarget.Peak,
            currentAxis = ScoreAxis.MOOD,
            lastScoreOnCurrentAxis = 0.2f,
            lastFeatures = null
        )
        assertEquals(NextTrackTarget.PEAK_SCORE, score, 0.001f)
        assertEquals(ScoreAxis.DANCE, axis)
    }

    @Test
    fun `Cooldown resolves to 0_3 on MOOD axis`() {
        val uc = usecase(emptyMap())
        val (score, axis) = uc.resolveTarget(
            target = NextTrackTarget.Cooldown,
            currentAxis = ScoreAxis.DANCE,
            lastScoreOnCurrentAxis = 0.9f,
            lastFeatures = null
        )
        assertEquals(NextTrackTarget.COOLDOWN_SCORE, score, 0.001f)
        assertEquals(ScoreAxis.MOOD, axis)
    }

    @Test
    fun `Hold preserves last score on current axis`() {
        val uc = usecase(emptyMap())
        val (score, axis) = uc.resolveTarget(
            target = NextTrackTarget.Hold,
            currentAxis = ScoreAxis.DANCE,
            lastScoreOnCurrentAxis = 0.6f,
            lastFeatures = null
        )
        assertEquals(0.6f, score, 0.001f)
        assertEquals(ScoreAxis.DANCE, axis)
    }

    @Test
    fun `Warmup raises by WARMUP_DELTA and clamps to 1`() {
        val uc = usecase(emptyMap())
        val (low, _) = uc.resolveTarget(
            NextTrackTarget.Warmup, ScoreAxis.DANCE, 0.5f, null
        )
        assertEquals(0.5f + NextTrackTarget.WARMUP_DELTA, low, 0.001f)

        val (high, _) = uc.resolveTarget(
            NextTrackTarget.Warmup, ScoreAxis.DANCE, 0.95f, null
        )
        assertEquals(1f, high, 0.001f)
    }

    @Test
    fun `Chill lowers by CHILL_DELTA and clamps to 0`() {
        val uc = usecase(emptyMap())
        val (mid, _) = uc.resolveTarget(
            NextTrackTarget.Chill, ScoreAxis.DANCE, 0.5f, null
        )
        assertEquals(0.5f - NextTrackTarget.CHILL_DELTA, mid, 0.001f)

        val (low, _) = uc.resolveTarget(
            NextTrackTarget.Chill, ScoreAxis.DANCE, 0.05f, null
        )
        assertEquals(0f, low, 0.001f)
    }

    @Test
    fun `SwitchAxis flips axis and uses last score on new axis`() {
        val features = feat("x", bpm = 200f, energy = 80f, valence = 30f, acoustic = 70f)
        val uc = usecase(mapOf("x" to features))
        val (_, axis) = uc.resolveTarget(
            NextTrackTarget.SwitchAxis,
            currentAxis = ScoreAxis.DANCE,
            lastScoreOnCurrentAxis = 0.8f,
            lastFeatures = features
        )
        assertEquals(ScoreAxis.MOOD, axis)
    }

    @Test
    fun `Hold falls back to 0_5 when no last context`() {
        val uc = usecase(emptyMap())
        val (score, _) = uc.resolveTarget(
            NextTrackTarget.Hold, ScoreAxis.DANCE, null, null
        )
        assertEquals(SuggestNextTrackUseCase.NEUTRAL_WHEN_NO_CONTEXT, score, 0.001f)
    }

    @Test
    fun `Absolute target is clamped and preserves axis`() {
        val uc = usecase(emptyMap())
        val (score, axis) = uc.resolveTarget(
            NextTrackTarget.Absolute(1.5f, ScoreAxis.MOOD),
            ScoreAxis.DANCE, null, null
        )
        assertEquals(1f, score, 0.001f)
        assertEquals(ScoreAxis.MOOD, axis)
    }

    // ── Ranking behaviour ────────────────────────────────────────────────

    @Test
    fun `Peak ranks higher-BPM tracks higher when no context`() = runBlocking {
        val pool = (0 until 10).map { track("t$it") }
        val features = pool.mapIndexed { idx, t ->
            // BPM od 140 do 230 — DANCE composite powinien rosnąć ~monotonicznie
            t.id!! to feat(t.id!!, bpm = 140f + idx * 10f, energy = 50f + idx * 5f)
        }.toMap()

        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = emptySet(),
            lastPickedTrack = null,
            target = NextTrackTarget.Peak,
            k = 3
        )
        // Top-3 to powinny być najszybsze utwory (t7, t8, t9 w dowolnej kolejności)
        val topIds = result.candidates.map { it.track.id }.toSet()
        assertTrue("Expected high-BPM tracks in top-3, got $topIds",
            topIds.contains("t9") || topIds.contains("t8") || topIds.contains("t7"))
    }

    @Test
    fun `harmonic compatibility favors matching Camelot key over distant keys`() = runBlocking {
        // Last played: 8B. Kompatybilne: 8B, 7B, 9B, 8A. Daleko: 2A, 3A.
        val last = track("last")
        val pool = listOf(
            track("same_key"),     // 8B — zgodne
            track("far_key"),      // 2A — daleko
            track("neighbor"),     // 7B — sąsiad
        )
        val features = mapOf(
            "last" to feat("last", bpm = 170f, camelot = "8B"),
            "same_key" to feat("same_key", bpm = 170f, camelot = "8B"),
            "far_key" to feat("far_key", bpm = 170f, camelot = "2A"),
            "neighbor" to feat("neighbor", bpm = 170f, camelot = "7B"),
        )

        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = emptySet(),
            lastPickedTrack = last,
            target = NextTrackTarget.Hold,  // fit-wise wszystko równe (ten sam BPM/energy)
            currentAxis = ScoreAxis.DANCE,
            k = 3
        )

        val firstId = result.candidates.first().track.id
        // "far_key" nie powinien być pierwszy
        assertFalse("far_key ranked first unexpectedly — harmonic weight ignored?",
            firstId == "far_key")
    }

    @Test
    fun `bpm jump penalty discourages big jumps`() = runBlocking {
        val last = track("last")
        val pool = listOf(
            track("same_bpm"),    // 170 BPM
            track("big_jump"),    // 210 BPM — duży skok
        )
        val features = mapOf(
            "last" to feat("last", bpm = 170f, camelot = "8B"),
            "same_bpm" to feat("same_bpm", bpm = 170f, camelot = "8B"),
            "big_jump" to feat("big_jump", bpm = 210f, camelot = "8B"),
        )

        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = emptySet(),
            lastPickedTrack = last,
            target = NextTrackTarget.Hold,
            currentAxis = ScoreAxis.DANCE,
            k = 2
        )

        // same_bpm musi być pierwszy bo ma 0 skoku BPM i zgodny klucz
        assertEquals("same_bpm", result.candidates.first().track.id)
    }

    @Test
    fun `candidate fields are populated correctly`() = runBlocking {
        val last = track("last")
        val pool = listOf(track("c"))
        val features = mapOf(
            "last" to feat("last", bpm = 170f, camelot = "8B"),
            "c" to feat("c", bpm = 180f, camelot = "9B"),
        )

        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = emptySet(),
            lastPickedTrack = last,
            target = NextTrackTarget.Hold,
            currentAxis = ScoreAxis.DANCE
        )

        val c = result.candidates.first()
        assertNotNull(c)
        assertEquals(10f, c.bpmDelta, 0.001f)
        assertTrue("harmonicCompat should be > 0 for neighbor keys", c.harmonicCompat > 0f)
        assertEquals(ScoreAxis.DANCE, c.scoreAxis)
    }

    @Test
    fun `resolvedTarget is exposed in Suggestion`() = runBlocking {
        val pool = listOf(track("t0"))
        val features = mapOf("t0" to feat("t0", bpm = 170f))

        val result = usecase(features).suggest(
            pool = pool,
            alreadyPickedIds = emptySet(),
            lastPickedTrack = null,
            target = NextTrackTarget.Cooldown
        )

        assertEquals(NextTrackTarget.COOLDOWN_SCORE, result.resolvedTargetScore, 0.001f)
        assertEquals(ScoreAxis.MOOD, result.resolvedAxis)
    }
}
