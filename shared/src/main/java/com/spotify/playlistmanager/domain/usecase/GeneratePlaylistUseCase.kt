package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.model.*
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use-case generowania playlisty z obsługą krzywych energii.
 *
 * Algorytm dla każdego segmentu (PlaylistSource):
 *  1. Pobierz wszystkie utwory z playlisty źródłowej
 *  2. Odfiltruj już użyte utwory (excludeTrackIds)
 *  3. Jeśli krzywa = None → sortuj wg SortOption i weź N
 *  4. Jeśli krzywa ≠ None → pobierz audio features z cache,
 *     dopasuj greedy algorytmem do krzywej
 *
 * Smooth Join: opcjonalnie wygładza przejścia między segmentami.
 */
@Singleton
class GeneratePlaylistUseCase @Inject constructor(
    private val repository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository
) {

    /**
     * Wynik generowania rozszerzony o informację o wyczerpaniu.
     *
     * @param generateResult standardowy wynik generowania
     * @param exhaustedPlaylists playlisty, które zostały wyczerpane
     * @param exhaustionStatuses status wyczerpania per playlista źródłowa
     * @param allGeneratedTrackIds ID wszystkich wygenerowanych utworów (do akumulacji w sesji)
     */
    data class GenerateWithExhaustionResult(
        val generateResult: GenerateResult,
        val exhaustedPlaylists: List<ExhaustionStatus>,
        val exhaustionStatuses: List<ExhaustionStatus>,
        val allGeneratedTrackIds: Set<String>
    )

    /**
     * Generuje playlistę z krzywymi energii i obsługą deduplikacji.
     *
     * @param sources segmenty z konfiguracją
     * @param smoothJoin czy wygładzać przejścia między segmentami
     * @param excludeTrackIds zbiór ID utworów do pominięcia (globalna deduplikacja)
     * @return pełny wynik z danymi do wykresu i informacją o wyczerpaniu
     */
    suspend fun generateWithCurves(
        sources: List<PlaylistSource>,
        smoothJoin: Boolean = true,
        excludeTrackIds: Set<String> = emptySet()
    ): GenerateWithExhaustionResult {
        val allSegments = mutableListOf<SegmentMatchResult>()
        val allTracks = mutableListOf<Track>()
        val newlyUsedIds = mutableSetOf<String>()
        val exhaustionStatuses = mutableListOf<ExhaustionStatus>()
        val exhaustedPlaylists = mutableListOf<ExhaustionStatus>()
        var prevLastScore: Float? = null

        // Łączona exclusion lista: zewnętrzna + wewnątrz-generacyjna
        val runningExclude = excludeTrackIds.toMutableSet()

        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue
            val playlistName = source.playlist?.name ?: "?"

            val allPlaylistTracks = fetchTracks(playlistId)
            val available = allPlaylistTracks.filter { it.id !in runningExclude }

            // Status wyczerpania dla tej playlisty
            val status = ExhaustionStatus(
                playlistId = playlistId,
                playlistName = playlistName,
                totalTracks = allPlaylistTracks.size,
                usedTracks = allPlaylistTracks.size - available.size
            )

            // Sprawdź czy playlista ma wystarczająco utworów
            if (available.isEmpty()) {
                exhaustedPlaylists.add(status.copy(
                    usedTracks = allPlaylistTracks.size
                ))
                exhaustionStatuses.add(status.copy(
                    usedTracks = allPlaylistTracks.size
                ))
                continue
            }

            if (source.energyCurve is EnergyCurve.None) {
                val sorted = applySorting(available, source.sortBy)
                val taken = sorted.take(source.trackCount)
                allTracks.addAll(taken)

                // Aktualizuj running exclusion
                val takenIds = taken.mapNotNull { it.id }.toSet()
                newlyUsedIds.addAll(takenIds)
                runningExclude.addAll(takenIds)

                val featuresMap = loadFeaturesMap(taken)
                val matchedTracks = taken.map { track ->
                    val score = featuresMap[track.id]
                        ?.let { CompositeScoreCalculator.calculate(it) }
                        ?: CompositeScoreCalculator.DEFAULT_SCORE
                    MatchedTrack(track, score, 0f)
                }
                allSegments.add(SegmentMatchResult(
                    tracks = matchedTracks,
                    targetScores = emptyList(),
                    matchPercentage = 1f,
                    lastScore = matchedTracks.lastOrNull()?.compositeScore ?: 0f
                ))
                prevLastScore = allSegments.last().lastScore

                // Zaktualizuj status po pobraniu
                val updatedStatus = status.copy(
                    usedTracks = allPlaylistTracks.size - available.size + taken.size
                )
                exhaustionStatuses.add(updatedStatus)
                if (updatedStatus.exhausted) exhaustedPlaylists.add(updatedStatus)
            } else {
                val featuresMap = loadFeaturesMap(available)

                val result = EnergyCurveCalculator.matchTracks(
                    tracks = available,
                    featuresMap = featuresMap,
                    curve = source.energyCurve,
                    trackCount = source.trackCount,
                    smoothJoin = smoothJoin,
                    prevLastScore = prevLastScore
                )

                val matchedTrackObjects = result.tracks.map { it.track }
                allTracks.addAll(matchedTrackObjects)

                // Aktualizuj running exclusion
                val matchedIds = matchedTrackObjects.mapNotNull { it.id }.toSet()
                newlyUsedIds.addAll(matchedIds)
                runningExclude.addAll(matchedIds)

                allSegments.add(result)
                prevLastScore = result.lastScore

                val updatedStatus = status.copy(
                    usedTracks = allPlaylistTracks.size - available.size + matchedTrackObjects.size
                )
                exhaustionStatuses.add(updatedStatus)
                if (updatedStatus.exhausted) exhaustedPlaylists.add(updatedStatus)
            }
        }

        val overallMatch = if (allSegments.isEmpty()) 1f
        else {
            val curveSegments = allSegments.filter { it.targetScores.isNotEmpty() }
            if (curveSegments.isEmpty()) 1f
            else curveSegments.map { it.matchPercentage }.average().toFloat()
        }

        val generateResult = GenerateResult(
            tracks = allTracks,
            segments = allSegments,
            overallMatchPercentage = overallMatch
        )

        return GenerateWithExhaustionResult(
            generateResult = generateResult,
            exhaustedPlaylists = exhaustedPlaylists,
            exhaustionStatuses = exhaustionStatuses,
            allGeneratedTrackIds = newlyUsedIds
        )
    }

    /**
     * Proste generowanie bez krzywych (backward compatibility).
     * Obsługuje excludeTrackIds do deduplikacji.
     */
    suspend operator fun invoke(
        sources: List<PlaylistSource>,
        excludeTrackIds: Set<String> = emptySet()
    ): List<Track> {
        val result = mutableListOf<Track>()
        val runningExclude = excludeTrackIds.toMutableSet()

        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue
            val allTracks = fetchTracks(playlistId)
            val available = allTracks.filter { it.id !in runningExclude }

            val sorted = if (source.sortBy != SortOption.NONE)
                applySorting(available, source.sortBy)
            else available

            val taken = sorted.take(source.trackCount)
            result.addAll(taken)
            runningExclude.addAll(taken.mapNotNull { it.id })
        }

        return result
    }

    /**
     * Oblicza status wyczerpania dla podanych playlist źródłowych.
     * Przydatne do wyświetlenia podglądu wyczerpania bez generowania.
     */
    suspend fun calculateExhaustionStatuses(
        sources: List<PlaylistSource>,
        usedTrackIds: Set<String>
    ): List<ExhaustionStatus> {
        return sources.mapNotNull { source ->
            val playlistId = source.playlist?.id ?: return@mapNotNull null
            val playlistName = source.playlist?.name ?: "?"
            val allTracks = fetchTracks(playlistId)
            val usedInPlaylist = allTracks.count { it.id in usedTrackIds }
            ExhaustionStatus(
                playlistId = playlistId,
                playlistName = playlistName,
                totalTracks = allTracks.size,
                usedTracks = usedInPlaylist
            )
        }
    }

    // ── Prywatne helpery ─────────────────────────────────────────────────

    private suspend fun fetchTracks(playlistId: String): List<Track> =
        if (playlistId == LIKED_SONGS_ID) repository.getLikedTracks()
        else repository.getPlaylistTracks(playlistId)

    private suspend fun loadFeaturesMap(tracks: List<Track>): Map<String, TrackAudioFeatures> =
        featuresRepository.getFeaturesMap(tracks.mapNotNull { it.id })

    private fun applySorting(tracks: List<Track>, option: SortOption): List<Track> =
        when (option) {
            SortOption.POPULARITY   -> tracks.sortedByDescending { it.popularity }
            SortOption.DURATION     -> tracks.sortedBy { it.durationMs }
            SortOption.RELEASE_DATE -> tracks.sortedByDescending { it.album }
            SortOption.NONE         -> tracks
        }

    companion object {
        const val LIKED_SONGS_ID = "__liked__"
    }
}
