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
import com.spotify.playlistmanager.domain.model.HarmonicOptimizer
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneratePlaylistUseCase @Inject constructor(
    private val repository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository
) {

    data class GenerateWithExhaustionResult(
        val generateResult: GenerateResult,
        val exhaustedPlaylists: List<ExhaustionStatus>,
        val exhaustionStatuses: List<ExhaustionStatus>,
        val allGeneratedTrackIds: Set<String>
    )

    /**
     * Backward-compat invoke — SortOption, bez krzywych energii.
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

            val sorted = pinnedTracks + applySorting(nonPinned, source.sortBy)
            val taken = sorted.take(source.trackCount)
            result.addAll(taken)
            runningExclude.addAll(taken.mapNotNull { it.id })
        }

        return result
    }

    /**
     * Generuje playlistę z krzywymi energii, filtrowaniem i opcjonalnym harmonic mixing.
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
        var prevAxis: ScoreAxis? = null

        val runningExclude = excludeTrackIds.toMutableSet()

        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue
            val playlistName = source.playlist?.name ?: "?"

            val sourceTracks = fetchTracks(playlistId)
            val allPlaylistTracks = mergePinnedFromExternalPlaylists(sourceTracks, source, playlistId)

            val pinnedIds = source.pinnedTracks.map { it.id }.toSet()
            val pinnedTracks = allPlaylistTracks.filter { it.id in pinnedIds }
            val nonPinnedAvailable = allPlaylistTracks.filter {
                it.id !in runningExclude && it.id !in pinnedIds
            }
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
                val sorted = pinnedTracks + applySorting(nonPinnedAvailable, source.sortBy)
                val taken = sorted.take(source.trackCount)
                allTracks.addAll(taken)

                takenIds = taken.mapNotNull { it.id }.toSet()
                newlyUsedIds.addAll(takenIds)
                runningExclude.addAll(takenIds)

                val featuresMap = loadFeaturesMap(taken)
                val matchedTracks = taken.map { track ->
                    val score = featuresMap[track.id]
                        ?.let { CompositeScoreCalculator.calculate(it) }
                        ?: CompositeScoreCalculator.DEFAULT_SCORE
                    // targetScore = actualScore: brak krzywej → utwór "celuje" w swój własny score
                    MatchedTrack(track, score, score)
                }
                allSegments.add(
                    SegmentMatchResult(
                        tracks = matchedTracks,
                        targetScores = emptyList(),
                        matchPercentage = 1f,
                        lastScore = matchedTracks.lastOrNull()?.compositeScore ?: 0f,
                        scoreAxis = ScoreAxis.DANCE
                    )
                )
                // None curve — brak spójności osi dla smooth join z następnym segmentem
                prevLastScore = null
                prevAxis = null
            } else {
                val featuresMap = loadFeaturesMap(available)
                val segment = EnergyCurveCalculator.matchTracks(
                    tracks = available,
                    featuresMap = featuresMap,
                    curve = source.energyCurve,
                    pinnedTrackIds = pinnedIds.toList(),
                    trackCount = source.trackCount,
                    smoothJoin = smoothJoin,
                    prevLastScore = prevLastScore,
                    prevAxis = prevAxis
                )

                // ── Optymalizacja harmoniczna (opcjonalna) ────────────────
                val finalSegment = if (source.harmonicMixing && segment.tracks.size > 2) {
                    val optimized = HarmonicOptimizer.optimize(segment.tracks, featuresMap)
                    segment.copy(tracks = optimized)
                } else {
                    segment
                }

                allSegments.add(finalSegment)
                allTracks.addAll(finalSegment.tracks.map { it.track })

                takenIds = finalSegment.tracks.mapNotNull { it.track.id }.toSet()
                newlyUsedIds.addAll(takenIds)
                runningExclude.addAll(takenIds)
                prevLastScore = finalSegment.lastScore
                prevAxis = finalSegment.scoreAxis
            }

            // Kumulatywna liczba użytych: wszystkie tracki tej playlisty
            // które są w runningExclude (= poprzednie rundy + właśnie wzięte)
            val sourceTrackIds = sourceTracks.mapNotNull { it.id }.toSet()
            val usedFromSource = sourceTrackIds.count { it in runningExclude }
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
            SortOption.RELEASE_DATE -> tracks.sortedByDescending { it.releaseDate ?: "" }
            SortOption.NONE -> tracks
        }

    companion object {
        const val LIKED_SONGS_ID = "__liked__"
    }
}
