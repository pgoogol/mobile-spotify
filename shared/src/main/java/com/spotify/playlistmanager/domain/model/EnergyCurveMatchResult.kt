package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track

/**
 * Wynik dopasowania utworów do krzywej energii dla jednego segmentu.
 */
data class SegmentMatchResult(
    /** Dopasowane utwory w kolejności krzywej. */
    val tracks: List<MatchedTrack>,
    /** Docelowe score'y krzywej. */
    val targetScores: List<Float>,
    /** Procent dopasowania: 1 − avg(|target − actual|). Zakres [0, 1]. */
    val matchPercentage: Float,
    /** Score ostatniego utworu (do smooth join z następnym segmentem). */
    val lastScore: Float
)

/**
 * Utwór z dołączonym composite score.
 */
data class MatchedTrack(
    val track: Track,
    val compositeScore: Float,
    val targetScore: Float
)

/**
 * Pełny wynik generowania playlisty z krzywymi energii.
 */
data class GenerateResult(
    /** Wszystkie dopasowane utwory we właściwej kolejności. */
    val tracks: List<Track>,
    /** Wyniki per segment (do wykresu). */
    val segments: List<SegmentMatchResult>,
    /** Globalny procent dopasowania. */
    val overallMatchPercentage: Float
)
