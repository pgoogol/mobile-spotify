package com.spotify.playlistmanager.domain.model

/**
 * Nastrój/cel doboru dla pojedynczego kolejnego utworu w trybie krok po kroku.
 *
 * W przeciwieństwie do [EnergyCurve] (która generuje N targetów dla całego segmentu),
 * [NextTrackTarget] określa cel jednej pozycji, zwykle relatywny do ostatnio
 * zagranego utworu ("podgrzej o 0.15") lub absolutny ("peak na 0.9").
 *
 * Tryby:
 * - [Peak]     — docelowo bardzo wysoka energia (DANCE ~0.9)
 * - [Warmup]   — podgrzej o [WARMUP_DELTA] względem ostatniego
 * - [Hold]     — utrzymaj poziom ostatniego (target = lastScore)
 * - [Chill]    — schłódź o [CHILL_DELTA] względem ostatniego
 * - [Cooldown] — wyciszenie (MOOD ~0.3)
 * - [SwitchAxis] — ten sam poziom co ostatni, ale druga oś (DANCE↔MOOD)
 * - [Absolute] — dokładny cel i oś, podane wprost
 *
 * Rozwiązanie targetu do pary (score, axis) robi [com.spotify.playlistmanager.domain.usecase.SuggestNextTrackUseCase].
 */
sealed class NextTrackTarget {

    /** Przycisk „Peak" — wysoka energia taneczna, niezależnie od historii. */
    data object Peak : NextTrackTarget()

    /** Przycisk „Podgrzej" — relatywny do ostatniego. */
    data object Warmup : NextTrackTarget()

    /** Przycisk „Trzymaj" — target = score ostatniego, na tej samej osi. */
    data object Hold : NextTrackTarget()

    /** Przycisk „Schłódź" — relatywny w dół. */
    data object Chill : NextTrackTarget()

    /** Przycisk „Cooldown" — niska energia MOOD, finał. */
    data object Cooldown : NextTrackTarget()

    /** Przycisk „Zmień klimat" — ten sam poziom, druga oś. */
    data object SwitchAxis : NextTrackTarget()

    /** Dowolny cel — gdy user ustawia ręcznie (np. slider). */
    data class Absolute(val score: Float, val axis: ScoreAxis) : NextTrackTarget()

    companion object {
        /** Docelowy poziom dla [Peak] na osi DANCE. */
        const val PEAK_SCORE = 0.9f

        /** Docelowy poziom dla [Cooldown] na osi MOOD. */
        const val COOLDOWN_SCORE = 0.3f

        /** Delta dla [Warmup]. */
        const val WARMUP_DELTA = 0.15f

        /** Delta dla [Chill]. */
        const val CHILL_DELTA = 0.15f
    }
}
