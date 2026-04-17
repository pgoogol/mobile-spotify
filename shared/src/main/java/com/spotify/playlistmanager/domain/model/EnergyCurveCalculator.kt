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
 * Kontynuacja: gdy prevLastScore != null i krzywa jest monotoniczna,
 * wszystkie targety (po auto-range) są przeskalowane tak, żeby segment
 * zaczynał od prevLastScore i kończył na naturalnym końcu puli.
 * Kontynuacja pomijana gdy poprzedni segment używał innej osi score'u.
 * Jeśli zakres jest za mały lub wyczerpany — zwracany jest ContinuationStatus.
 */
object EnergyCurveCalculator {

    /** Tolerancja dla losowego wyboru spośród kandydatów. */
    const val DEFAULT_TOLERANCE = 0.05f

    /** Minimalna różnica start→end, żeby krzywa była uznana za monotoniczną. */
    private const val MIN_MONOTONIC_NET = 0.10f

    /** Minimalny pozostały zakres, poniżej którego emitujemy Warning. */
    private const val MIN_CONTINUATION_RANGE = 0.08f

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
     * @param enableContinuation czy kontynuować od prevLastScore (tylko gdy `prevAxis == curve.scoreAxis`)
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
        enableContinuation: Boolean = true,
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

        // Kontynuacja: tylko gdy poprzedni segment używał tej samej osi
        val continuationApplicable = enableContinuation && prevAxis == axis
        val (effectiveTargets, continuationStatus) = applyContinuation(
            rescaledTargets,
            continuationApplicable,
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
                        targetScore = effectiveTargets[idx]
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
                    targetScore = effectiveTargets[idx]
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
            targetScores = effectiveTargets,
            matchPercentage = matchPercentage,
            lastScore = matched.lastOrNull()?.compositeScore ?: 0f,
            scoreAxis = axis,
            continuationStatus = continuationStatus
        )
    }

    private data class ContinuationResult(
        val effectiveTargets: List<Float>,
        val status: ContinuationStatus
    )

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
     * Przeskalowuje targety (po auto-range) tak, żeby segment zaczynał od prevLastScore.
     * Działa tylko dla krzywych monotonicznie rosnących lub malejących
     * (|naturalEnd - naturalStart| >= MIN_MONOTONIC_NET).
     */
    private fun applyContinuation(
        targets: List<Float>,
        enabled: Boolean,
        prevLastScore: Float?
    ): ContinuationResult {
        if (!enabled || prevLastScore == null || targets.isEmpty()) {
            return ContinuationResult(targets, ContinuationStatus.Ok)
        }

        val naturalStart = targets.first()
        val naturalEnd = targets.last()
        val netChange = naturalEnd - naturalStart

        if (abs(netChange) < MIN_MONOTONIC_NET) {
            // Krzywa niezbyt monotoniczna (fala, trapez, plateau) — nie przeskalowujemy
            return ContinuationResult(targets, ContinuationStatus.Ok)
        }

        val ascending = netChange > 0

        if (ascending) {
            if (prevLastScore <= naturalStart) {
                return ContinuationResult(targets, ContinuationStatus.Ok)
            }
            if (prevLastScore >= naturalEnd) {
                return ContinuationResult(targets, ContinuationStatus.Impossible(naturalEnd))
            }
            val remainingRange = naturalEnd - prevLastScore
            val rescaled = targets.map { t ->
                prevLastScore + (t - naturalStart) / netChange * remainingRange
            }
            val status = if (remainingRange < MIN_CONTINUATION_RANGE) {
                ContinuationStatus.Warning(remainingRange)
            } else {
                ContinuationStatus.Ok
            }
            return ContinuationResult(rescaled, status)
        } else {
            // Opadająca
            if (prevLastScore >= naturalStart) {
                return ContinuationResult(targets, ContinuationStatus.Ok)
            }
            if (prevLastScore <= naturalEnd) {
                return ContinuationResult(targets, ContinuationStatus.Impossible(naturalEnd))
            }
            val remainingRange = prevLastScore - naturalEnd
            val rescaled = targets.map { t ->
                prevLastScore + (t - naturalStart) / netChange * remainingRange
            }
            val status = if (remainingRange < MIN_CONTINUATION_RANGE) {
                ContinuationStatus.Warning(remainingRange)
            } else {
                ContinuationStatus.Ok
            }
            return ContinuationResult(rescaled, status)
        }
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
