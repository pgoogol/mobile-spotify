package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.data.model.SortOption
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.EnergyCurveCalculator
import com.spotify.playlistmanager.domain.model.ExhaustionStatus
import com.spotify.playlistmanager.domain.model.GenerateResult
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use-case generowania playlisty z obsługą krzywych energii.
 *
 * Algorytm dla każdego segmentu (PlaylistSource):
 *  1. Pobierz wszystkie utwory z playlisty źródłowej
 *  2. Dołóż pinned tracks z OBCYCH playlist (jeśli są w source.pinnedTracks)
 *  3. Odfiltruj już użyte utwory (excludeTrackIds), z wyłączeniem pinned
 *  4. Jeśli krzywa = None → sortuj wg SortOption i weź N
 *  5. Jeśli krzywa ≠ None → pobierz audio features z cache,
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
     */
    data class GenerateWithExhaustionResult(
        val generateResult: GenerateResult,
        val exhaustedPlaylists: List<ExhaustionStatus>,
        val exhaustionStatuses: List<ExhaustionStatus>,
        val allGeneratedTrackIds: Set<String>
    )

    /**
     * Backward-compat invoke — używa SortOption, bez krzywych energii.
     * Pinned tracks z OBCYCH playlist również są tu uwzględniane.
     */
    suspend operator fun invoke(
        sources: List<PlaylistSource>,
        excludeTrackIds: Set<String> = emptySet()
    ): List<Track> {
        val result = mutableListOf<Track>()
        val runningExclude = excludeTrackIds.toMutableSet()

        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue
            val sourceTracks = fetchTracks(playlistId)
            val allTracks = mergePinnedFromExternalPlaylists(sourceTracks, source, playlistId)

            val pinnedIds = source.pinnedTracks.map { it.id }.toSet()
            val pinnedTracks = allTracks.filter { it.id in pinnedIds }
            val nonPinned = allTracks.filter {
                it.id !in pinnedIds && it.id !in runningExclude
            }

            // Pinned na początku, reszta wg SortOption
            val sorted = pinnedTracks + applySorting(nonPinned, source.sortBy)
            val taken = sorted.take(source.trackCount)
            result.addAll(taken)
            runningExclude.addAll(taken.mapNotNull { it.id })
        }

        return result
    }

    /**
     * Generuje playlistę z krzywymi energii i obsługą deduplikacji.
     *
     * @param sources segmenty z konfiguracją
     * @param smoothJoin czy wygładzać przejścia między segmentami
     * @param excludeTrackIds zbiór ID utworów do pominięcia (globalna deduplikacja)
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

            val sourceTracks = fetchTracks(playlistId)
            // Dokleja pinned z obcych playlist do puli, z której wybieramy
            val allPlaylistTracks = mergePinnedFromExternalPlaylists(sourceTracks, source, playlistId)

            // Pinned tracks ignorują excludeTrackIds (deduplikacja sesyjna)
            val pinnedIds = source.pinnedTracks.map { it.id }.toSet()
            val pinnedTracks = allPlaylistTracks.filter { it.id in pinnedIds }
            val nonPinnedAvailable = allPlaylistTracks.filter {
                it.id !in runningExclude && it.id !in pinnedIds
            }
            // Połącz: pinned (zawsze) + non-pinned (po filtrze exclude)
            val available = pinnedTracks + nonPinnedAvailable

            if (available.isEmpty()) {
                val emptyStatus = ExhaustionStatus(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    totalTracks = sourceTracks.size,
                    usedTracks = sourceTracks.size
                )
                exhaustedPlaylists.add(emptyStatus)
                exhaustionStatuses.add(emptyStatus)
                continue
            }

            val takenIds: Set<String>

            if (source.energyCurve is EnergyCurve.None) {
                // Pinned na początku, reszta sortowana
                val sorted = pinnedTracks + applySorting(nonPinnedAvailable, source.sortBy)
                val taken = sorted.take(source.trackCount)
                allTracks.addAll(taken)

                // Aktualizuj running exclusion
                takenIds = taken.mapNotNull { it.id }.toSet()
                newlyUsedIds.addAll(takenIds)
                runningExclude.addAll(takenIds)

                val featuresMap = loadFeaturesMap(taken)
                val matchedTracks = taken.map { track ->
                    val score = featuresMap[track.id]
                        ?.let { CompositeScoreCalculator.calculate(it) }
                        ?: CompositeScoreCalculator.DEFAULT_SCORE
                    MatchedTrack(track, score, 0f)
                }
                allSegments.add(
                    SegmentMatchResult(
                        tracks = matchedTracks,
                        targetScores = emptyList(),
                        matchPercentage = 1f,
                        lastScore = matchedTracks.lastOrNull()?.compositeScore ?: 0f
                    )
                )
            } else {
                // Krzywa ≠ None — deleguj do EnergyCurveCalculator
                val featuresMap = loadFeaturesMap(available)
                val segment = EnergyCurveCalculator.matchTracks(
                    tracks = available,
                    featuresMap = featuresMap,
                    curve = source.energyCurve,
                    pinnedTrackIds = pinnedIds.toList(),
                    trackCount = source.trackCount,
                    smoothJoin = smoothJoin,
                    prevLastScore = prevLastScore
                )
                allSegments.add(segment)
                allTracks.addAll(segment.tracks.map { it.track })

                takenIds = segment.tracks.mapNotNull { it.track.id }.toSet()
                newlyUsedIds.addAll(takenIds)
                runningExclude.addAll(takenIds)
                prevLastScore = segment.lastScore
            }

            // ── Status wyczerpania liczony WYŁĄCZNIE po playliście źródła ───
            // usedTracks = ile tracków z playlisty źródła zostało wziętych
            // w bieżącym segmencie (external pinned się nie liczą, bo ich ID
            // nie występują w sourceTracks).
            val sourceTrackIds = sourceTracks.mapNotNull { it.id }.toSet()
            val usedFromSource = takenIds.count { it in sourceTrackIds }
            exhaustionStatuses.add(
                ExhaustionStatus(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    totalTracks = sourceTracks.size,
                    usedTracks = usedFromSource
                )
            )
        }

        val overallMatch =
            if (allSegments.isEmpty()) 0f
            else allSegments.map { it.matchPercentage }.average().toFloat()

        return GenerateWithExhaustionResult(
            generateResult = GenerateResult(
                tracks = allTracks,
                segments = allSegments,
                overallMatchPercentage = overallMatch
            ),
            exhaustedPlaylists = exhaustedPlaylists,
            exhaustionStatuses = exhaustionStatuses,
            allGeneratedTrackIds = newlyUsedIds
        )
    }

    /**
     * Oblicza status wyczerpania dla podanych playlist źródłowych.
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

    /**
     * Dokleja do listy `sourceTracks` te pinned utwory, które pochodzą
     * z OBCYCH playlist (sourcePlaylistId != segmentPlaylistId i fullTrack != null).
     *
     * Pinned utwory pochodzące z playlisty źródła segmentu są już w sourceTracks
     * — nie dublujemy. Dedup po Track.id.
     */
    private fun mergePinnedFromExternalPlaylists(
        sourceTracks: List<Track>,
        source: PlaylistSource,
        segmentPlaylistId: String
    ): List<Track> {
        if (source.pinnedTracks.isEmpty()) return sourceTracks

        val sourceIds = sourceTracks.mapNotNull { it.id }.toHashSet()
        val externalPinned = source.pinnedTracks
            .asSequence()
            .filter { pinned ->
                pinned.fullTrack != null &&
                        pinned.sourcePlaylistId != null &&
                        pinned.sourcePlaylistId != segmentPlaylistId &&
                        pinned.id !in sourceIds
            }
            .mapNotNull { it.fullTrack }
            .toList()

        return if (externalPinned.isEmpty()) sourceTracks
        else sourceTracks + externalPinned
    }

    private suspend fun loadFeaturesMap(tracks: List<Track>): Map<String, TrackAudioFeatures> =
        featuresRepository.getFeaturesMap(tracks.mapNotNull { it.id })

    private fun applySorting(tracks: List<Track>, option: SortOption): List<Track> =
        when (option) {
            SortOption.POPULARITY -> tracks.sortedByDescending { it.popularity }
            SortOption.DURATION -> tracks.sortedBy { it.durationMs }
            SortOption.RELEASE_DATE -> tracks.sortedByDescending { it.album }
            SortOption.NONE -> tracks
        }

    companion object {
        const val LIKED_SONGS_ID = "__liked__"
    }
}