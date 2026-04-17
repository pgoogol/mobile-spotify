package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track

/**
 * Status kontynuacji segmentu względem poprzedniego.
 */
sealed class ContinuationStatus {
    /** Kontynuacja przebiegła normalnie lub segment jest pierwszy. */
    data object Ok : ContinuationStatus()

    /**
     * Poprzedni segment zakończył się blisko górnej granicy tej krzywej —
     * segment ma mało miejsca do kontynuacji.
     * @param remainingRange pozostały zakres [0..1]
     */
    data class Warning(val remainingRange: Float) : ContinuationStatus()

    /**
     * Poprzedni segment zakończył się poza zakresem tej krzywej —
     * kontynuacja niemożliwa, wybierz inną strategię.
     * @param naturalEnd naturalna granica krzywej, której nie można osiągnąć
     */
    data class Impossible(val naturalEnd: Float) : ContinuationStatus()
}

/**
 * Wynik dopasowania utworów do krzywej energii dla jednego segmentu.
 */
data class SegmentMatchResult(
    /** Dopasowane utwory w kolejności krzywej. */
    val tracks: List<MatchedTrack>,
    /** Docelowe score'y — po auto-range i kontynuacji (ta sama skala co `compositeScore`). */
    val targetScores: List<Float>,
    /** Procent dopasowania: 1 − avg(|target − actual|). Zakres [0, 1]. */
    val matchPercentage: Float,
    /** Score ostatniego utworu (do kontynuacji z następnym segmentem tej samej osi). */
    val lastScore: Float,
    /** Oś, na której policzono [lastScore] i `compositeScore` utworów (DANCE/MOOD). */
    val scoreAxis: ScoreAxis = ScoreAxis.DANCE,
    /** Status kontynuacji względem poprzedniego segmentu. */
    val continuationStatus: ContinuationStatus = ContinuationStatus.Ok,
    /**
     * Numer rundy generowania, do której należy ten segment.
     * 0 = nieznana (backward compat dla starych testów / ścieżki bez krzywych).
     * Nadawane przez ViewModel przy akumulacji segmentów w sesji.
     */
    val roundNumber: Int = 0
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
