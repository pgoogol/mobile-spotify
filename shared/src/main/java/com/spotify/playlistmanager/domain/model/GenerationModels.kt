package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track

/**
 * Tryb generowania playlisty.
 *
 * EXHAUST — generuj wszystko z szablonu, powtarzaj aż do wyczerpania puli.
 * SEGMENT — generuj dokładną porcję z szablonu, iteracyjnie buduj playlistę.
 */
enum class GenerationMode {
    EXHAUST,
    SEGMENT
}

/**
 * Cel wyjściowy wygenerowanych utworów.
 * W trybie SEGMENT użytkownik może wybrać kilka jednocześnie.
 */
enum class TargetAction {
    /** Utwórz nową playlistę na Spotify. */
    NEW_PLAYLIST,
    /** Dodaj do istniejącej playlisty na Spotify. */
    EXISTING_PLAYLIST,
    /** Dodaj do kolejki odtwarzania. */
    QUEUE
}

/**
 * Runda generowania — wpis w historii sesji.
 *
 * @param roundNumber numer kolejny rundy w sesji (1-based)
 * @param templateName nazwa szablonu użytego do generowania
 * @param trackIds ID wygenerowanych utworów w tej rundzie
 * @param tracks wygenerowane utwory (do podglądu/undo)
 * @param timestamp czas wygenerowania
 * @param generationMode tryb użyty w tej rundzie
 */
data class GenerationRound(
    val roundNumber: Int,
    val templateName: String,
    val trackIds: Set<String>,
    val tracks: List<Track>,
    val timestamp: Long = System.currentTimeMillis(),
    val generationMode: GenerationMode
)

/**
 * Status wyczerpania puli dla jednej playlisty źródłowej.
 *
 * @param playlistId ID playlisty
 * @param playlistName nazwa playlisty
 * @param totalTracks łączna liczba utworów w playliście
 * @param usedTracks liczba już użytych (w excludeTrackIds) utworów
 * @param exhausted czy playlista jest w pełni wyczerpana
 */
data class ExhaustionStatus(
    val playlistId: String,
    val playlistName: String,
    val totalTracks: Int,
    val usedTracks: Int
) {
    val exhausted: Boolean get() = usedTracks >= totalTracks
    val remainingTracks: Int get() = (totalTracks - usedTracks).coerceAtLeast(0)
    val usagePercent: Float get() =
        if (totalTracks == 0) 1f else usedTracks.toFloat() / totalTracks
}
