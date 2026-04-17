package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import kotlin.math.abs

/**
 * Algorytm dopasowania utworów do strategii ([EnergyCurve]) jednego segmentu.
 *
 * Strategia:
 *  1. Degrade strategii dla małych segmentów (Arc/Valley z <3 utworów → Rising/Falling)
 *  2. Oblicz score dla każdego utworu wg [EnergyCurve.scoreAxis] (DANCE lub MOOD)
 *  3. Auto-range: skaluj logiczne targety krzywej [0..1] do rozkładu p5-p95 puli
 *  4. Posortuj pulę po score (raz)
 *  5. Dla każdej pozycji: binary search najbliższego kandydata z tolerancją 0.05
 *  6. Shuffle within tolerance: losowy wybór spośród kandydatów w zakresie
 *
 * Auto-range sprawia, że strategia „Narastająco" w bachatowej playliście
 * (composite ~0.15–0.35) zmatchuje tracki w jej rozpiętości, a nie próbuje
 * sięgnąć 0.9 (którego tam nie ma). Analogicznie dla MOOD axis i strategii
 * Romantic/Calm.
 *
 * Złożoność: O(n × log m) gdzie n = pozycje krzywej, m = rozmiar puli
 *
 * Smooth Join: miękkie przejście między segmentami o tej samej osi score'u —
 * jeśli osie różnią się, smooth join jest pomijany (prevLastScore jest w
 * innej skali score niż bieżący segment).
 */
object EnergyCurveCalculator {

    /** Tolerancja dla losowego wyboru spośród kandydatów. */
    const val DEFAULT_TOLERANCE = 0.05f

    /** Stała bazowa maxDelta dla smooth join. */
    private const val SMOOTH_JOIN_BASE_DELTA = 0.15f

    /** Współczynnik proporcjonalny smooth join. */
    private const val SMOOTH_JOIN_FACTOR = 0.5f

    /** Minimalna liczba utworów puli, przy której auto-range ma sens. */
    private const val MIN_POOL_FOR_RESCALE = 5

    /** Minimalna rozpiętość p95-p5, przy której auto-range ma sens. */
    private const val MIN_RANGE_FOR_RESCALE = 0.10f

    /**
     * Dopasowuje utwory z puli do strategii segmentu.
     *
     * @param tracks      pula dostępnych utworów
     * @param featuresMap mapa spotifyTrackId → audio features
     * @param curve       strategia doboru (kształt + oś)
     * @param trackCount  liczba pozycji do wypełnienia
     * @param tolerance   tolerancja dla shuffle within tolerance
     * @param smoothJoin  czy zastosować smooth join (tylko gdy `prevAxis == curve.scoreAxis`)
     * @param prevLastScore composite score ostatniego utworu poprzedniego segmentu
     * @param prevAxis    oś, z której pochodzi [prevLastScore] (null = brak poprzedniego segmentu)
     * @return wynik dopasowania
     */
    fun matchTracks(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>,
        curve: EnergyCurve,
        pinnedTrackIds: List<String> = emptyList(),
        trackCount: Int,
        tolerance: Float = DEFAULT_TOLERANCE,
        smoothJoin: Boolean = true,
        prevLastScore: Float? = null,
        prevAxis: ScoreAxis? = null
    ): SegmentMatchResult {
        // Degrade strategii dla krótkich segmentów (Arc/Valley → Rising/Falling)
        val effectiveCurve = curve.degradeFor(trackCount)
        val axis = effectiveCurve.scoreAxis

        val logicalTargets = effectiveCurve.generateTargets(trackCount)

        // Rozdziel pulę na pinned i non-pinned
        val pinnedSet = pinnedTrackIds.toSet()
        val pinnedTracks = tracks.filter { it.id in pinnedSet }
        val nonPinnedTracks = tracks.filter { it.id !in pinnedSet }

        if (logicalTargets.isEmpty()) {
            // Strategia None — pinned na początku, reszta dopełniona
            val remaining = trackCount - pinnedTracks.size
            val taken = pinnedTracks + nonPinnedTracks.take(remaining.coerceAtLeast(0))
            return SegmentMatchResult(
                tracks = taken.map { track ->
                    val score = featuresMap[track.id]
                        ?.let { CompositeScoreCalculator.calculate(it, axis) }
                        ?: CompositeScoreCalculator.DEFAULT_SCORE
                    MatchedTrack(track, score, 0f)
                },
                targetScores = emptyList(),
                matchPercentage = 1f,
                lastScore = 0f,
                scoreAxis = axis
            )
        }

        // ── Scoring puli wg wybranej osi ─────────────────────────────
        val scoredPinned = pinnedTracks.map { track ->
            val score = featuresMap[track.id]
                ?.let { CompositeScoreCalculator.calculate(it, axis) }
                ?: CompositeScoreCalculator.DEFAULT_SCORE
            ScoredTrack(track, score)
        }

        val scoredNonPinned = nonPinnedTracks.map { track ->
            val score = featuresMap[track.id]
                ?.let { CompositeScoreCalculator.calculate(it, axis) }
                ?: CompositeScoreCalculator.DEFAULT_SCORE
            ScoredTrack(track, score)
        }

        // ── Auto-range: skaluj logiczne targety do rozkładu puli ────
        val allScores = (scoredPinned + scoredNonPinned).map { it.score }
        val rescaledTargets = rescaleToPoolPercentiles(logicalTargets, allScores)

        // Smooth join: tylko gdy poprzedni segment używał tej samej osi
        val joinApplicable = smoothJoin && prevAxis == axis
        val effectiveTargets = applySmoothJoin(
            rescaledTargets,
            joinApplicable,
            prevLastScore
        )

        // ── Faza 1: Przypisz pinned tracks do optymalnych pozycji ────
        data class PinnedCandidate(
            val track: ScoredTrack,
            val position: Int,
            val distance: Float
        )

        val allCandidates = scoredPinned.flatMap { pinned ->
            effectiveTargets.indices.map { pos ->
                PinnedCandidate(
                    pinned, pos,
                    abs(pinned.score - effectiveTargets[pos])
                )
            }
        }.sortedBy { it.distance }

        val pinnedAssignments = mutableMapOf<Int, ScoredTrack>()
        val assignedPositions = mutableSetOf<Int>()
        val assignedTracks = mutableSetOf<String>()

        for (candidate in allCandidates) {
            val trackId = candidate.track.track.id ?: continue
            if (trackId in assignedTracks) continue
            if (candidate.position in assignedPositions) continue
            pinnedAssignments[candidate.position] = candidate.track
            assignedPositions.add(candidate.position)
            assignedTracks.add(trackId)
            if (pinnedAssignments.size == scoredPinned.size) break
        }

        // ── Faza 2: Wypełnij wolne sloty z puli (bez pinned) ─────────
        val sortedPool = scoredNonPinned.sortedBy { it.score }
        val available = sortedPool.toMutableList()
        val matched = mutableListOf<MatchedTrack>()

        for ((idx, target) in effectiveTargets.withIndex()) {
            val pinned = pinnedAssignments[idx]
            if (pinned != null) {
                matched.add(
                    MatchedTrack(
                        track = pinned.track,
                        compositeScore = pinned.score,
                        targetScore = rescaledTargets[idx]
                    )
                )
                continue
            }

            if (available.isEmpty()) break
            val selected = findBestMatch(available, target, tolerance)
            available.remove(selected)

            matched.add(
                MatchedTrack(
                    track = selected.track,
                    compositeScore = selected.score,
                    targetScore = rescaledTargets[idx]
                )
            )
        }

        // Procent dopasowania — oba wymiary w tej samej skali (rescaled)
        val matchPercentage = if (matched.isEmpty()) 1f
        else {
            val avgDeviation = matched.map {
                abs(it.compositeScore - it.targetScore)
            }.average().toFloat()
            (1f - avgDeviation).coerceIn(0f, 1f)
        }

        return SegmentMatchResult(
            tracks = matched,
            targetScores = rescaledTargets,
            matchPercentage = matchPercentage,
            lastScore = matched.lastOrNull()?.compositeScore ?: 0f,
            scoreAxis = axis
        )
    }

    /**
     * Skaluj logiczne targety [0..1] do rozkładu puli (p5-p95).
     *
     * Fallback (brak rescale) gdy:
     * - pula zbyt mała (< MIN_POOL_FOR_RESCALE)
     * - rozpiętość p95-p5 za wąska (< MIN_RANGE_FOR_RESCALE)
     *
     * Visible for testing.
     */
    internal fun rescaleToPoolPercentiles(
        logicalTargets: List<Float>,
        poolScores: List<Float>
    ): List<Float> {
        if (logicalTargets.isEmpty() || poolScores.size < MIN_POOL_FOR_RESCALE) {
            return logicalTargets
        }
        val sorted = poolScores.sorted()
        val p5 = percentile(sorted, 0.05f)
        val p95 = percentile(sorted, 0.95f)
        val range = p95 - p5
        if (range < MIN_RANGE_FOR_RESCALE) return logicalTargets
        return logicalTargets.map { p5 + it.coerceIn(0f, 1f) * range }
    }

    /**
     * Percentyl z posortowanej listy (interpolacja liniowa między sąsiednimi indeksami).
     *
     * @param sorted lista posortowana rosnąco
     * @param q kwantyl w [0..1]
     */
    internal fun percentile(sorted: List<Float>, q: Float): Float {
        if (sorted.isEmpty()) return 0f
        if (sorted.size == 1) return sorted[0]
        val idx = q.coerceIn(0f, 1f) * (sorted.size - 1)
        val lo = idx.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.lastIndex)
        val frac = idx - lo
        return sorted[lo] + (sorted[hi] - sorted[lo]) * frac
    }

    /**
     * Aplikuje smooth join — modyfikuje target pierwszej pozycji.
     */
    private fun applySmoothJoin(
        targets: List<Float>,
        smoothJoin: Boolean,
        prevLastScore: Float?
    ): List<Float> {
        if (!smoothJoin || prevLastScore == null || targets.isEmpty()) return targets

        val firstTarget = targets[0]
        val maxDelta = minOf(
            SMOOTH_JOIN_BASE_DELTA,
            abs(prevLastScore - firstTarget) * SMOOTH_JOIN_FACTOR
        )

        val effectiveFirst = (prevLastScore + firstTarget) / 2f
        // Clamp do maxDelta od oryginalnego target
        val clamped = firstTarget + (effectiveFirst - firstTarget).coerceIn(-maxDelta, maxDelta)

        return listOf(clamped) + targets.drop(1)
    }

    /**
     * Binary search + shuffle within tolerance.
     *
     * Znajduje kandydatów w zakresie [target - tolerance, target + tolerance],
     * losuje jednego. Jeśli brak kandydatów w tolerancji, zwraca najbliższego.
     */
    private fun findBestMatch(
        sortedPool: List<ScoredTrack>,
        target: Float,
        tolerance: Float
    ): ScoredTrack {
        if (sortedPool.size == 1) return sortedPool[0]

        // Binary search — znajdź insert point
        val insertIdx = sortedPool.binarySearchInsertPoint(target)

        // Szukaj kandydatów w tolerancji
        val candidates = mutableListOf<ScoredTrack>()

        // Skanuj w lewo od insert point
        var left = insertIdx - 1
        while (left >= 0 && abs(sortedPool[left].score - target) <= tolerance) {
            candidates.add(sortedPool[left])
            left--
        }

        // Skanuj w prawo od insert point
        var right = insertIdx
        while (right < sortedPool.size && abs(sortedPool[right].score - target) <= tolerance) {
            candidates.add(sortedPool[right])
            right++
        }

        if (candidates.isNotEmpty()) {
            return candidates.random()
        }

        // Brak w tolerancji — znajdź najbliższego
        val lo = (insertIdx - 1).coerceIn(0, sortedPool.lastIndex)
        val hi = insertIdx.coerceIn(0, sortedPool.lastIndex)
        return if (abs(sortedPool[lo].score - target) <= abs(sortedPool[hi].score - target)) {
            sortedPool[lo]
        } else {
            sortedPool[hi]
        }
    }

    /**
     * Binary search: zwraca index, na który target zostałby wstawiony.
     */
    private fun List<ScoredTrack>.binarySearchInsertPoint(target: Float): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (this[mid].score < target) lo = mid + 1
            else hi = mid
        }
        return lo
    }

    private data class ScoredTrack(val track: Track, val score: Float)
}
