package com.spotify.playlistmanager.domain.dj.model

/**
 * Filtry wejścia do puli dla pojedynczego bloku — spec sekcja 8.
 *
 * Mapowane z presetów ([Preset]) i z opcji UI. Stosowane jako twarde filtry
 * (utwór nie przechodzący filtru w ogóle nie wchodzi do puli kandydatów).
 */
data class BlockFilters(
    /** Odrzuca utwory z `tempoFeeling > 0.85` (bardzo szybkie). */
    val noVeryFast: Boolean = false,
    /** Tylko utwory starsze niż `classicsOlderThanYear` (np. 2010). */
    val classicsOnly: Boolean = false,
    val classicsOlderThanYear: Int = 2010,
    /** Min próg `danceFlowScore` (np. 0.55 dla "bez ryzyka"). */
    val minDanceFlow: Float? = null,
    /** Tylko utwory z tym podstylem (filtr "więcej Timby"). */
    val onlySubstyle: Substyle? = null
)
