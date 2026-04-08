package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.SortOption
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Use-case wyszukiwania kandydatów do wymiany pojedynczego utworu.
 *
 * Strategia:
 *  - Z playlisty źródłowej pobieramy wszystkie utwory
 *  - Odfiltrowujemy excludeTrackIds (utwory już użyte w sesji + sam wymieniany)
 *  - Dla krzywej ≠ None sortujemy kandydatów wg |score - currentScore|
 *    (najmniejsza różnica = najlepsze dopasowanie do pozycji krzywej)
 *  - Dla krzywej = None sortujemy wg SortOption źródła (popularność/długość/data wydania)
 *
 * Nie modyfikuje żadnego stanu — zwraca tylko listę kandydatów.
 */
@Singleton
class FindReplacementsUseCase @Inject constructor(
    private val repository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository
) {

    /**
     * Kandydat do wymiany z dołączonymi metrykami do UI.
     *
     * @param track utwór kandydat
     * @param compositeScore composite score kandydata (lub DEFAULT_SCORE gdy brak features)
     * @param scoreDifference |candidateScore - currentScore| — 0 dla krzywej None
     * @param hasFeatures czy kandydat ma audio features w cache (wpływa na wyświetlanie)
     */
    data class ReplacementCandidate(
        val track: Track,
        val compositeScore: Float,
        val scoreDifference: Float,
        val hasFeatures: Boolean
    )

    /**
     * Znajduje kandydatów do wymiany dla podanego utworu.
     *
     * @param sourcePlaylistId ID playlisty źródłowej (skąd pochodził oryginał)
     * @param currentCompositeScore composite score wymienianego utworu (do sortowania)
     * @param excludeTrackIds utwory do pominięcia — powinno zawierać ID wszystkich
     *                       utworów aktualnie w podglądzie (w tym wymieniany)
     * @param energyCurve krzywa energii używana w źródle — decyduje o strategii sortowania
     * @param sortBy opcja sortowania gdy [energyCurve] = None
     * @param maxResults maksymalna liczba kandydatów (domyślnie 10)
     * @return lista kandydatów posortowana wg dopasowania (najlepsi pierwsi),
     *         pusta gdy źródło wyczerpane
     */
    suspend operator fun invoke(
        sourcePlaylistId: String,
        currentCompositeScore: Float,
        excludeTrackIds: Set<String>,
        energyCurve: EnergyCurve,
        sortBy: SortOption,
        maxResults: Int = 10
    ): List<ReplacementCandidate> {
        val allTracks = fetchTracks(sourcePlaylistId)
        val available = allTracks.filter { it.id != null && it.id !in excludeTrackIds }
        if (available.isEmpty()) return emptyList()

        val featuresMap = featuresRepository.getFeaturesMap(available.mapNotNull { it.id })

        return if (energyCurve is EnergyCurve.None) {
            // Bez krzywej — sortujemy wg SortOption, score tylko informacyjnie
            val sorted = applySorting(available, sortBy)
            sorted.take(maxResults).map { track ->
                val features = featuresMap[track.id]
                val score = features
                    ?.let { CompositeScoreCalculator.calculate(it) }
                    ?: CompositeScoreCalculator.DEFAULT_SCORE
                ReplacementCandidate(
                    track = track,
                    compositeScore = score,
                    scoreDifference = 0f,
                    hasFeatures = features != null
                )
            }
        } else {
            // Z krzywą — sortujemy wg |score - currentScore|
            available
                .map { track ->
                    val features = featuresMap[track.id]
                    val score = features
                        ?.let { CompositeScoreCalculator.calculate(it) }
                        ?: CompositeScoreCalculator.DEFAULT_SCORE
                    ReplacementCandidate(
                        track = track,
                        compositeScore = score,
                        scoreDifference = abs(score - currentCompositeScore),
                        hasFeatures = features != null
                    )
                }
                .sortedBy { it.scoreDifference }
                .take(maxResults)
        }
    }

    // ── Helpery prywatne ────────────────────────────────────────────────

    private suspend fun fetchTracks(playlistId: String): List<Track> =
        if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
            repository.getLikedTracks()
        else
            repository.getPlaylistTracks(playlistId)

    private fun applySorting(tracks: List<Track>, option: SortOption): List<Track> =
        when (option) {
            SortOption.POPULARITY -> tracks.sortedByDescending { it.popularity }
            SortOption.DURATION -> tracks.sortedBy { it.durationMs }
            SortOption.RELEASE_DATE -> tracks.sortedByDescending { it.album }
            SortOption.NONE -> tracks
        }
}