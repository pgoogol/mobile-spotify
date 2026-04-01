package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import kotlin.math.abs

/**
 * Algorytm dopasowania utworów do krzywej energii.
 *
 * Strategia:
 *  1. Oblicz composite score dla każdego utworu z puli
 *  2. Posortuj pulę po composite score (raz)
 *  3. Dla każdej pozycji krzywej: binary search najbliższego kandydata
 *  4. Shuffle within tolerance: jeśli kilka utworów mieści się w tolerancji,
 *     losowy wybór spośród nich
 *
 * Złożoność: O(n × log m) gdzie n = pozycje krzywej, m = rozmiar puli
 *
 * Smooth Join: miękkie przejście między segmentami — efektywny target
 * pierwszego utworu segmentu B uwzględnia ostatni score segmentu A.
 * maxDelta = min(0.15, |prevLastScore - target[0]| × 0.5)
 */
object EnergyCurveCalculator {

    /** Tolerancja dla losowego wyboru spośród kandydatów. */
    const val DEFAULT_TOLERANCE = 0.05f

    /** Stała bazowa maxDelta dla smooth join. */
    private const val SMOOTH_JOIN_BASE_DELTA = 0.15f

    /** Współczynnik proporcjonalny smooth join. */
    private const val SMOOTH_JOIN_FACTOR = 0.5f

    /**
     * Dopasowuje utwory z puli do krzywej energii jednego segmentu.
     *
     * @param tracks      pula dostępnych utworów
     * @param featuresMap mapa spotifyTrackId → audio features
     * @param curve       krzywa energii
     * @param trackCount  liczba pozycji do wypełnienia
     * @param tolerance   tolerancja dla shuffle within tolerance
     * @param smoothJoin  czy zastosować smooth join
     * @param prevLastScore composite score ostatniego utworu poprzedniego segmentu (null = brak)
     * @return wynik dopasowania
     */
    fun matchTracks(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>,
        curve: EnergyCurve,
        trackCount: Int,
        tolerance: Float = DEFAULT_TOLERANCE,
        smoothJoin: Boolean = true,
        prevLastScore: Float? = null
    ): SegmentMatchResult {
        val targets = curve.generateTargets(trackCount)

        if (targets.isEmpty()) {
            // Krzywa None — zwróć utwory bez dopasowania
            val taken = tracks.take(trackCount)
            return SegmentMatchResult(
                tracks = taken.map { track ->
                    val score = featuresMap[track.id]
                        ?.let { CompositeScoreCalculator.calculate(it) }
                        ?: CompositeScoreCalculator.DEFAULT_SCORE
                    MatchedTrack(track, score, 0f)
                },
                targetScores = emptyList(),
                matchPercentage = 1f,
                lastScore = 0f
            )
        }

        // Oblicz composite scores i posortuj
        val scoredPool = tracks.map { track ->
            val score = featuresMap[track.id]
                ?.let { CompositeScoreCalculator.calculate(it) }
                ?: CompositeScoreCalculator.DEFAULT_SCORE
            ScoredTrack(track, score)
        }.sortedBy { it.score }

        // Mutable list do usuwania wybranych
        val available = scoredPool.toMutableList()

        // Modyfikuj targets z smooth join
        val effectiveTargets = applySmoothJoin(targets, smoothJoin, prevLastScore)

        val matched = mutableListOf<MatchedTrack>()

        for ((idx, target) in effectiveTargets.withIndex()) {
            if (available.isEmpty()) break

            val selected = findBestMatch(available, target, tolerance)
            available.remove(selected)

            matched.add(MatchedTrack(
                track = selected.track,
                compositeScore = selected.score,
                targetScore = targets[idx] // oryginalne targety do wykresu
            ))
        }

        // Procent dopasowania
        val matchPercentage = if (matched.isEmpty()) 1f
        else {
            val avgDeviation = matched.map { abs(it.compositeScore - it.targetScore) }.average().toFloat()
            (1f - avgDeviation).coerceIn(0f, 1f)
        }

        return SegmentMatchResult(
            tracks = matched,
            targetScores = targets,
            matchPercentage = matchPercentage,
            lastScore = matched.lastOrNull()?.compositeScore ?: 0f
        )
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
