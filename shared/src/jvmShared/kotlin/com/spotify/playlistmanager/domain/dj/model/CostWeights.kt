package com.spotify.playlistmanager.domain.dj.model

/**
 * Wagi członów funkcji kosztu w `BlockGenerator` — spec sekcja 8.2.
 *
 * Dwa tryby pracy mają różne priorytety:
 * - **Planning (Tryb A):** budżet czasu i unikanie ogranych ważniejsze,
 *   skoki energii mniej krytyczne (cały plan widać w kontekście).
 * - **Live (Tryb B):** płynność przejść i mały skok energii krytyczne
 *   (każde przejście słychać natychmiast).
 */
data class CostWeights(
    val energy: Float,
    val tempo: Float,
    val energyJump: Float,
    val artist: Float,
    val similar: Float,
    val aggressive: Float,
    val duration: Float,
    val danceflow: Float,
    val fresh: Float,
    val popularity: Float,
    val skip: Float
) {
    companion object {
        /** Tryb A — Planning (spec sekcja 8.2). */
        val PLANNING = CostWeights(
            energy = 1.00f,
            tempo = 0.40f,
            energyJump = 0.50f,
            artist = 1.50f,
            similar = 1.00f,
            aggressive = 0.60f,
            duration = 0.50f,
            danceflow = 0.25f,
            fresh = 0.40f,
            popularity = 0.20f,
            skip = 0.30f
        )

        /** Tryb B — Live (spec sekcja 8.2). */
        val LIVE = CostWeights(
            energy = 1.00f,
            tempo = 0.70f,
            energyJump = 0.90f,
            artist = 1.50f,
            similar = 1.00f,
            aggressive = 0.60f,
            duration = 0.20f,
            danceflow = 0.25f,
            fresh = 0.15f,
            popularity = 0.20f,
            skip = 0.30f
        )
    }
}
