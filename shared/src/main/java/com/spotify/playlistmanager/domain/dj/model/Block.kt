package com.spotify.playlistmanager.domain.dj.model

/**
 * Blok 3–10 utworów jednego stylu o wspólnym kształcie energii.
 * Spec sekcja 1: "Jednostką jest BLOK ... impreza to sekwencja bloków".
 *
 * `targetScores` — docelowe wartości EnergyScore dla każdego slotu (z kształtu).
 * Używane do wizualizacji krzywej i do `rerollSlot` (regenerujemy z tym samym celem).
 */
data class Block(
    val id: String,
    val style: Style,
    val shape: EnergyShape,
    val tracks: List<AnalyzedTrack>,
    val targetScores: List<Float>,
    /** Start anchor użyty przy budowie (null = tryb Planning). */
    val startAnchor: Float?,
    /** Lock per slot (soft-lock — utwór nie zostanie podmieniony przez reroll). */
    val lockedSlots: Set<Int> = emptySet()
) {
    val size: Int get() = tracks.size
    val totalDurationMs: Long
        get() = tracks.sumOf { it.track.durationMs.toLong() }
}
