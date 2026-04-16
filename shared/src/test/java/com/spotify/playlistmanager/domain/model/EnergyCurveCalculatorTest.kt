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
        id: String,
        bpm: Float = 170f,
        energy: Float = 70f,
        danceability: Float = 65f,
        valence: Float = 50f,
        acousticness: Float = 20f
    ) = TrackAudioFeatures(
        spotifyTrackId = id, bpm = bpm, energy = energy,
        danceability = danceability, valence = valence, acousticness = acousticness,
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

    // ══════════════════════════════════════════════════════════
    //  Podstawowe właściwości matchTracks
    // ══════════════════════════════════════════════════════════

    @Test
    fun `matchTracks returns correct number of tracks`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 8
        )
        assertEquals(8, result.tracks.size)
        assertEquals(8, result.targetScores.size)
    }

    @Test
    fun `matchTracks returns no duplicates`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 15
        )
        val ids = result.tracks.map { it.track.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `matchTracks handles empty pool`() {
        val result = EnergyCurveCalculator.matchTracks(
            emptyList(), emptyMap(), EnergyCurve.Rising, emptyList(), 5
        )
        assertTrue(result.tracks.isEmpty())
    }

    @Test
    fun `matchTracks handles pool smaller than trackCount`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool.take(3), testFeaturesMap, EnergyCurve.Rising, emptyList(), 10
        )
        assertEquals(3, result.tracks.size)
    }

    @Test
    fun `match percentage is between 0 and 1`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Arc, emptyList(), 10
        )
        assertTrue(result.matchPercentage in 0f..1f)
    }

    @Test
    fun `Rising matching follows ascending trend`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 10
        )
        val scores = result.tracks.map { it.compositeScore }
        assertTrue("Rising curve should have positive trend", scores.last() - scores.first() > 0)
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
            listOf(makeTrack("no-features")), emptyMap(), EnergyCurve.Rising, emptyList(), 1
        )
        assertEquals(0f, result.tracks[0].compositeScore, 0.001f)
    }

    @Test
    fun `smooth join with null prevLastScore has no effect`() {
        val r1 = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 5,
            smoothJoin = true, prevLastScore = null
        )
        val r2 = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 5,
            smoothJoin = false, prevLastScore = null
        )
        assertEquals(r1.targetScores, r2.targetScores)
    }

    @Test
    fun `lastScore equals last matched track composite score`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 5
        )
        assertEquals(result.tracks.last().compositeScore, result.lastScore, 0.001f)
    }

    @Test
    fun `scoreAxis in result matches curve scoreAxis`() {
        val danceResult = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 5
        )
        assertEquals(ScoreAxis.DANCE, danceResult.scoreAxis)

        val moodPool = buildMoodPool()
        val moodResult = EnergyCurveCalculator.matchTracks(
            moodPool.first, moodPool.second, EnergyCurve.Romantic, emptyList(), 5
        )
        assertEquals(ScoreAxis.MOOD, moodResult.scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Pinned tracks
    // ══════════════════════════════════════════════════════════

    @Test
    fun `matchTracks with pinned tracks includes all pinned`() {
        val pinned = listOf("t1", "t4")
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising,
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
            testPool, testFeaturesMap, EnergyCurve.Arc,
            pinnedTrackIds = pinned, trackCount = 5
        )
        val ids = result.tracks.map { it.track.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `matchTracks all pinned fills entire segment`() {
        val allIds = testPool.mapNotNull { it.id }
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising,
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

    // ══════════════════════════════════════════════════════════
    //  Arc degradacja
    // ══════════════════════════════════════════════════════════

    @Test
    fun `n=2 shrinks Arc to Rising in matchTracks`() {
        val smallPool = testPool.take(5)
        val resultArc = EnergyCurveCalculator.matchTracks(
            smallPool, testFeaturesMap, EnergyCurve.Arc, emptyList(), 2
        )
        // Arc zdegraduje do Rising — scoreAxis DANCE
        assertEquals(ScoreAxis.DANCE, resultArc.scoreAxis)
        assertEquals(2, resultArc.tracks.size)
        // Tracki powinny być w kolejności rosnącej (Rising)
        assertTrue(
            "Degraded Rising should be ascending",
            resultArc.tracks[1].compositeScore >= resultArc.tracks[0].compositeScore - 0.1f
        )
    }

    // ══════════════════════════════════════════════════════════
    //  Auto-range: rescaleToPoolPercentiles
    // ══════════════════════════════════════════════════════════

    @Test
    fun `rescale scales 0_1 targets to pool p5-p95`() {
        val poolScores = (0 until 20).map { it.toFloat() / 19f }  // 0.0 .. 1.0 równomiernie
        val logicalTargets = listOf(0f, 0.5f, 1f)
        val rescaled = EnergyCurveCalculator.rescaleToPoolPercentiles(logicalTargets, poolScores)

        val sorted = poolScores.sorted()
        val p5 = EnergyCurveCalculator.percentile(sorted, 0.05f)
        val p95 = EnergyCurveCalculator.percentile(sorted, 0.95f)

        assertEquals(p5, rescaled[0], 0.01f)
        assertEquals((p5 + p95) / 2f, rescaled[1], 0.02f)
        assertEquals(p95, rescaled[2], 0.01f)
    }

    @Test
    fun `rescale fallback when pool has fewer than 5 tracks`() {
        val smallPool = listOf(0.1f, 0.3f, 0.7f)
        val targets = listOf(0f, 0.5f, 1f)
        val result = EnergyCurveCalculator.rescaleToPoolPercentiles(targets, smallPool)
        assertEquals(targets, result)
    }

    @Test
    fun `rescale fallback when range is too narrow`() {
        val narrowPool = (0 until 10).map { 0.5f + it * 0.005f }  // rozpiętość < 0.10
        val targets = listOf(0f, 0.5f, 1f)
        val result = EnergyCurveCalculator.rescaleToPoolPercentiles(targets, narrowPool)
        assertEquals(targets, result)
    }

    @Test
    fun `rescale returns empty when targets empty`() {
        val result = EnergyCurveCalculator.rescaleToPoolPercentiles(emptyList(), listOf(0.1f, 0.5f, 0.9f))
        assertEquals(emptyList<Float>(), result)
    }

    @Test
    fun `rescale produces values within pool range`() {
        val poolScores = (0 until 20).map { 0.2f + it * 0.03f }  // 0.20 .. 0.77
        val targets = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val rescaled = EnergyCurveCalculator.rescaleToPoolPercentiles(targets, poolScores)
        val sorted = poolScores.sorted()
        val p5 = EnergyCurveCalculator.percentile(sorted, 0.05f)
        val p95 = EnergyCurveCalculator.percentile(sorted, 0.95f)
        rescaled.forEach { v ->
            assertTrue("Rescaled value $v outside [p5=$p5, p95=$p95]", v in p5..p95 + 0.001f)
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Oś MOOD
    // ══════════════════════════════════════════════════════════

    @Test
    fun `mood axis uses valence-based score for pool`() {
        // Dwa typy tracków: wysoka valence (mood score wyższy) vs niska
        val highMoodTracks = (0 until 10).map { makeTrack("hi$it") }
        val lowMoodTracks = (0 until 10).map { makeTrack("lo$it") }
        val featuresMap = mutableMapOf<String, TrackAudioFeatures>()
        highMoodTracks.forEach { t ->
            featuresMap[t.id!!] = makeFeatures(t.id!!, valence = 95f, acousticness = 10f, danceability = 80f)
        }
        lowMoodTracks.forEach { t ->
            featuresMap[t.id!!] = makeFeatures(t.id!!, valence = 10f, acousticness = 80f, danceability = 20f)
        }
        val allTracks = highMoodTracks + lowMoodTracks

        val result = EnergyCurveCalculator.matchTracks(
            allTracks, featuresMap, EnergyCurve.Romantic, emptyList(), 5
        )

        val resultIds = result.tracks.map { it.track.id }.toSet()
        val highIds = highMoodTracks.mapNotNull { it.id }.toSet()
        val matchedHigh = resultIds.count { it in highIds }
        assertTrue("Romantic should prefer high-valence tracks (got $matchedHigh/5)", matchedHigh >= 4)
    }

    // ══════════════════════════════════════════════════════════
    //  Stable picks near median
    // ══════════════════════════════════════════════════════════

    @Test
    fun `stable strategy picks tracks near median score`() {
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Stable, emptyList(), 5
        )
        val allScores = testFeaturesMap.values.map { CompositeScoreCalculator.calculate(it) }.sorted()
        val median = EnergyCurveCalculator.percentile(allScores, 0.5f)

        result.tracks.forEach { matched ->
            assertTrue(
                "Score ${matched.compositeScore} too far from median $median",
                kotlin.math.abs(matched.compositeScore - median) < 0.25f
            )
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Smooth join — ignorowany gdy różne osie
    // ══════════════════════════════════════════════════════════

    @Test
    fun `smooth join not applied when prev axis differs`() {
        // MOOD prev z DANCE current → smooth join ignorowany
        val resultWithJoin = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 5,
            smoothJoin = true, prevLastScore = 0.9f, prevAxis = ScoreAxis.MOOD
        )
        val resultWithoutJoin = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 5,
            smoothJoin = false, prevLastScore = null, prevAxis = null
        )
        // Targety powinny być identyczne (smooth join pominięty)
        assertEquals(resultWithJoin.targetScores, resultWithoutJoin.targetScores)
    }

    @Test
    fun `smooth join same axis does not crash and preserves track count`() {
        // targetScores = rescaledTargets (nie effectiveTargets) — smooth join działa wewnętrznie.
        // Weryfikujemy że wynik jest poprawny strukturalnie.
        val result = EnergyCurveCalculator.matchTracks(
            testPool, testFeaturesMap, EnergyCurve.Rising, emptyList(), 5,
            smoothJoin = true, prevLastScore = 0.3f, prevAxis = ScoreAxis.DANCE
        )
        assertEquals(5, result.tracks.size)
        assertEquals(5, result.targetScores.size)
        assertTrue(result.matchPercentage in 0f..1f)
    }

    // ══════════════════════════════════════════════════════════
    //  percentile helper
    // ══════════════════════════════════════════════════════════

    @Test
    fun `percentile of empty list returns 0`() {
        assertEquals(0f, EnergyCurveCalculator.percentile(emptyList(), 0.5f), 0.001f)
    }

    @Test
    fun `percentile of single element returns that element`() {
        assertEquals(0.7f, EnergyCurveCalculator.percentile(listOf(0.7f), 0.5f), 0.001f)
    }

    @Test
    fun `percentile p0 returns minimum`() {
        val sorted = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        assertEquals(0.1f, EnergyCurveCalculator.percentile(sorted, 0f), 0.001f)
    }

    @Test
    fun `percentile p100 returns maximum`() {
        val sorted = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
        assertEquals(0.9f, EnergyCurveCalculator.percentile(sorted, 1f), 0.001f)
    }

    @Test
    fun `percentile p50 of symmetric list returns middle`() {
        val sorted = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        assertEquals(0.5f, EnergyCurveCalculator.percentile(sorted, 0.5f), 0.001f)
    }

    // ══════════════════════════════════════════════════════════
    //  Pomocnicze
    // ══════════════════════════════════════════════════════════

    private fun buildMoodPool(): Pair<List<Track>, Map<String, TrackAudioFeatures>> {
        val tracks = (0 until 20).map { makeTrack("m$it") }
        val features = tracks.associate { t ->
            val i = t.id!!.substring(1).toInt()
            t.id!! to makeFeatures(
                t.id!!, bpm = 150f, energy = 60f, danceability = 60f,
                valence = 10f + i * 4.5f, acousticness = 90f - i * 4f
            )
        }
        return tracks to features
    }
}
