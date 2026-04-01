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
 *  2. Jeśli krzywa = None → sortuj wg SortOption i weź N
 *  3. Jeśli krzywa ≠ None → pobierz audio features z cache,
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
     * Generuje playlistę z krzywymi energii.
     *
     * @param sources segmenty z konfiguracją
     * @param smoothJoin czy wygładzać przejścia między segmentami
     * @return pełny wynik z danymi do wykresu
     */
    suspend fun generateWithCurves(
        sources: List<PlaylistSource>,
        smoothJoin: Boolean = true
    ): GenerateResult {
        val allSegments = mutableListOf<SegmentMatchResult>()
        val allTracks = mutableListOf<Track>()
        var prevLastScore: Float? = null

        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue

            val tracks = fetchTracks(playlistId)

            if (source.energyCurve is EnergyCurve.None) {
                // Standardowe sortowanie bez krzywej
                val sorted = applySorting(tracks, source.sortBy)
                val taken = sorted.take(source.trackCount)
                allTracks.addAll(taken)

                // Wygeneruj pusty segment result
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
            } else {
                // Dopasowanie do krzywej energii
                val featuresMap = loadFeaturesMap(tracks)

                val result = EnergyCurveCalculator.matchTracks(
                    tracks = tracks,
                    featuresMap = featuresMap,
                    curve = source.energyCurve,
                    trackCount = source.trackCount,
                    smoothJoin = smoothJoin,
                    prevLastScore = prevLastScore
                )

                allTracks.addAll(result.tracks.map { it.track })
                allSegments.add(result)
                prevLastScore = result.lastScore
            }
        }

        // Globalny procent dopasowania
        val overallMatch = if (allSegments.isEmpty()) 1f
        else {
            val curveSegments = allSegments.filter { it.targetScores.isNotEmpty() }
            if (curveSegments.isEmpty()) 1f
            else curveSegments.map { it.matchPercentage }.average().toFloat()
        }

        return GenerateResult(
            tracks = allTracks,
            segments = allSegments,
            overallMatchPercentage = overallMatch
        )
    }

    /**
     * Proste generowanie bez krzywych (backward compatibility).
     */
    suspend operator fun invoke(sources: List<PlaylistSource>): List<Track> {
        val result = mutableListOf<Track>()

        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue
            val allTracks = fetchTracks(playlistId)

            val sorted = if (source.sortBy != SortOption.NONE)
                applySorting(allTracks, source.sortBy)
            else allTracks

            result.addAll(sorted.take(source.trackCount))
        }

        return result
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
