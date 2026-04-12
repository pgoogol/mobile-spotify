package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import kotlin.math.abs

/**
 * Optymalizator harmoniczny — reorderuje utwory po dopasowaniu do krzywej energii
 * tak, aby sąsiednie utwory były harmonicznie kompatybilne (Camelot Wheel).
 *
 * Strategia: local swap z ograniczeniem.
 * Dla każdej pary sąsiadów sprawdzamy, czy zamiana z pobliskim utworem
 * (maxSwapDistance pozycji dalej) poprawia harmonię BEZ nadmiernego
 * pogarszania dopasowania do krzywej energii.
 *
 * Złożoność: O(passes × n × maxSwapDistance) — liniowa w praktyce.
 */
object HarmonicOptimizer {

    /** Maksymalna akceptowalna degradacja composite score vs target po swapie. */
    private const val MAX_ENERGY_PENALTY = 0.08f

    /** Liczba przebiegów optymalizacji. */
    private const val MAX_PASSES = 3

    /**
     * Optymalizuje kolejność utworów pod kątem kompatybilności harmonicznej.
     *
     * @param matched lista dopasowanych utworów (po EnergyCurveCalculator)
     * @param featuresMap mapa audio features (do odczytu Camelot key)
     * @param maxSwapDistance ile pozycji dalej szukać lepszego sąsiada (default 2)
     * @return zoptymalizowana lista (ten sam rozmiar, te same elementy, inna kolejność)
     */
    fun optimize(
        matched: List<MatchedTrack>,
        featuresMap: Map<String, TrackAudioFeatures>,
        maxSwapDistance: Int = 2
    ): List<MatchedTrack> {
        if (matched.size <= 2) return matched

        // Zbuduj mapę trackId → CamelotKey
        val keyMap = buildCamelotKeyMap(matched, featuresMap)

        val result = matched.toMutableList()
        var improved = true
        var pass = 0

        while (improved && pass < MAX_PASSES) {
            improved = false
            pass++

            for (i in 0 until result.size - 1) {
                val currentScore = pairHarmonicScore(result, i, keyMap)
                if (currentScore >= 0.85f) continue // już wystarczająco dobre

                // Szukaj lepszego sąsiada w zakresie [i+2, i+maxSwapDistance+1]
                val bestSwap = findBestSwap(result, i, maxSwapDistance, keyMap)
                if (bestSwap != null && bestSwap.second > currentScore) {
                    // Sprawdź czy swap nie psuje energy matching zbyt mocno
                    val j = bestSwap.first
                    if (isSwapAcceptable(result, i + 1, j)) {
                        // Wykonaj swap
                        val temp = result[i + 1]
                        result[i + 1] = result[j]
                        result[j] = temp
                        improved = true
                    }
                }
            }
        }

        return result
    }

    /**
     * Oblicza łączny koszt harmoniczny (suma 1 - score) dla sekwencji.
     * Niższy = lepszy.
     */
    fun totalHarmonicCost(
        matched: List<MatchedTrack>,
        featuresMap: Map<String, TrackAudioFeatures>
    ): Float {
        if (matched.size <= 1) return 0f
        val keyMap = buildCamelotKeyMap(matched, featuresMap)
        return (0 until matched.size - 1).sumOf { i ->
            (1f - pairHarmonicScore(matched, i, keyMap)).toDouble()
        }.toFloat()
    }

    /**
     * Oblicza średni score harmoniczny pary (i, i+1).
     * Zwraca 0.5 jeśli któryś z utworów nie ma klucza Camelota.
     */
    private fun pairHarmonicScore(
        list: List<MatchedTrack>,
        i: Int,
        keyMap: Map<String, CamelotWheel.CamelotKey>
    ): Float {
        val keyA = list[i].track.id?.let { keyMap[it] } ?: return 0.5f
        val keyB = list[i + 1].track.id?.let { keyMap[it] } ?: return 0.5f
        return CamelotWheel.compatibilityScore(keyA, keyB)
    }

    /**
     * Szuka najlepszego kandydata do swapu z pozycją i+1.
     * Zwraca (index, harmonicScore) lub null.
     */
    private fun findBestSwap(
        list: List<MatchedTrack>,
        i: Int,
        maxDist: Int,
        keyMap: Map<String, CamelotWheel.CamelotKey>
    ): Pair<Int, Float>? {
        val keyA = list[i].track.id?.let { keyMap[it] } ?: return null
        var bestIdx = -1
        var bestScore = 0f

        val rangeStart = i + 2
        val rangeEnd = minOf(i + 1 + maxDist, list.size - 1)

        for (j in rangeStart..rangeEnd) {
            val keyCandidate = list[j].track.id?.let { keyMap[it] } ?: continue
            val score = CamelotWheel.compatibilityScore(keyA, keyCandidate)
            if (score > bestScore) {
                bestScore = score
                bestIdx = j
            }
        }

        return if (bestIdx >= 0) bestIdx to bestScore else null
    }

    /**
     * Sprawdza czy swap nie pogarsza dopasowania do krzywej energii ponad limit.
     */
    private fun isSwapAcceptable(
        list: List<MatchedTrack>,
        idxA: Int,
        idxB: Int
    ): Boolean {
        val trackA = list[idxA]
        val trackB = list[idxB]

        // Po swapie: trackB trafi na pozycję idxA (z targetem idxA), trackA na idxB
        val penaltyA = abs(trackB.compositeScore - trackA.targetScore)
        val penaltyB = abs(trackA.compositeScore - trackB.targetScore)

        return penaltyA <= MAX_ENERGY_PENALTY && penaltyB <= MAX_ENERGY_PENALTY
    }

    private fun buildCamelotKeyMap(
        matched: List<MatchedTrack>,
        featuresMap: Map<String, TrackAudioFeatures>
    ): Map<String, CamelotWheel.CamelotKey> {
        val map = mutableMapOf<String, CamelotWheel.CamelotKey>()
        for (mt in matched) {
            val id = mt.track.id ?: continue
            val features = featuresMap[id] ?: continue
            val key = CamelotWheel.parseCamelot(features.camelot)
                ?: CamelotWheel.parseCamelot(features.musicalKey)
                ?: continue
            map[id] = key
        }
        return map
    }
}
