package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.TrackAudioFeatures

/**
 * Oblicza composite score dla utworu na podstawie cech audio.
 *
 * score = 0.45 × bpmNorm + 0.35 × energyNorm + 0.20 × danceNorm
 *
 * - BPM normalizowany do zakresu salsowego 130–220, clamp do [0, 1]
 * - Energy i Danceability z CSV w skali 0–100, normalizowane do [0, 1]
 * - Valence pominięty — nastrój ≠ energia taneczna
 * - Utwory bez audio features → score = 0.0
 */
object CompositeScoreCalculator {

    /** Wagi composite score. */
    const val WEIGHT_BPM = 0.45f
    const val WEIGHT_ENERGY = 0.35f
    const val WEIGHT_DANCE = 0.20f

    /** Zakres BPM dla normalizacji (poszerzony salsa range). */
    const val BPM_MIN = 130f
    const val BPM_MAX = 220f

    /**
     * Oblicza composite score dla podanych cech audio.
     * @return wartość z zakresu [0, 1]
     */
    fun calculate(features: TrackAudioFeatures): Float {
        val bpmNorm = normalizeBpm(features.bpm)
        val energyNorm = (features.energy / 100f).coerceIn(0f, 1f)
        val danceNorm = (features.danceability / 100f).coerceIn(0f, 1f)

        return WEIGHT_BPM * bpmNorm + WEIGHT_ENERGY * energyNorm + WEIGHT_DANCE * danceNorm
    }

    /**
     * Normalizuje BPM do zakresu [0, 1] z clamp.
     * BPM poniżej 130 → 0.0, powyżej 220 → 1.0.
     */
    fun normalizeBpm(bpm: Float): Float {
        if (bpm <= BPM_MIN) return 0f
        if (bpm >= BPM_MAX) return 1f
        return (bpm - BPM_MIN) / (BPM_MAX - BPM_MIN)
    }

    /**
     * Domyślny score dla utworów bez audio features w cache.
     */
    const val DEFAULT_SCORE = 0.0f
}
