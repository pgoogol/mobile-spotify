package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Oś dopasowania utworów — która funkcja score jest używana przez matcher.
 *
 * - [DANCE] = klasyczny composite „ile to się tańczy" (BPM + energy + danceability)
 * - [MOOD] = klimat/atmosfera utworu (valence + 1-acousticness + danceability)
 *
 * Strategia ([EnergyCurve]) wybiera jedną oś; wszystkie utwory w puli są
 * scorowane tym samym algorytmem, co daje matcherowi spójny ranking.
 */
@Serializable
enum class ScoreAxis {
    @SerialName("dance")
    DANCE,

    @SerialName("mood")
    MOOD
}

/**
 * Oblicza composite score dla utworu na podstawie cech audio.
 *
 * Dwa niezależne wymiary:
 * - DANCE: 0.45 × bpmNorm + 0.35 × energyNorm + 0.20 × danceNorm
 * - MOOD:  0.55 × valenceNorm + 0.25 × (1 − acousticnessNorm) + 0.20 × danceNorm
 *
 * - BPM normalizowany do zakresu salsowego 130–220, clamp do [0, 1]
 * - Energy, Danceability, Valence, Acousticness z CSV w skali 0–100 → [0, 1]
 * - Utwory bez audio features → score = 0.0
 */
object CompositeScoreCalculator {

    /** Wagi DANCE score. */
    const val WEIGHT_BPM = 0.45f
    const val WEIGHT_ENERGY = 0.35f
    const val WEIGHT_DANCE = 0.20f

    /** Wagi MOOD score. */
    const val WEIGHT_VALENCE = 0.55f
    const val WEIGHT_ACOUSTIC_INV = 0.25f
    const val WEIGHT_MOOD_DANCE = 0.20f

    /** Zakres BPM dla normalizacji (poszerzony salsa range). */
    const val BPM_MIN = 130f
    const val BPM_MAX = 220f

    /** Domyślny score dla utworów bez audio features w cache. */
    const val DEFAULT_SCORE = 0.0f

    /**
     * Oblicza DANCE composite score. [0, 1]
     */
    fun calculate(features: TrackAudioFeatures): Float {
        val bpmNorm = normalizeBpm(features.bpm)
        val energyNorm = (features.energy / 100f).coerceIn(0f, 1f)
        val danceNorm = (features.danceability / 100f).coerceIn(0f, 1f)

        return WEIGHT_BPM * bpmNorm + WEIGHT_ENERGY * energyNorm + WEIGHT_DANCE * danceNorm
    }

    /**
     * Oblicza MOOD composite score. [0, 1]
     *
     * Wysokie MOOD = „pozytywne/żywe/rytmiczne" (wysoka valence,
     * niska akustyczność = bardziej wyprodukowane, wysoka taneczność).
     * Niskie MOOD = „spokojne/akustyczne/melancholijne".
     */
    fun calculateMood(features: TrackAudioFeatures): Float {
        val valenceNorm = (features.valence / 100f).coerceIn(0f, 1f)
        val acousticInv = 1f - (features.acousticness / 100f).coerceIn(0f, 1f)
        val danceNorm = (features.danceability / 100f).coerceIn(0f, 1f)

        return WEIGHT_VALENCE * valenceNorm +
               WEIGHT_ACOUSTIC_INV * acousticInv +
               WEIGHT_MOOD_DANCE * danceNorm
    }

    /**
     * Score wybierany przez oś dopasowania.
     */
    fun calculate(features: TrackAudioFeatures, axis: ScoreAxis): Float = when (axis) {
        ScoreAxis.DANCE -> calculate(features)
        ScoreAxis.MOOD  -> calculateMood(features)
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
}
