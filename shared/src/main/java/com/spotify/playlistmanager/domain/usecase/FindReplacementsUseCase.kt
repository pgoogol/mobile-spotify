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

@Singleton
class FindReplacementsUseCase @Inject constructor(
    private val repository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository
) {

    data class ReplacementCandidate(
        val track: Track,
        val compositeScore: Float,
        val scoreDifference: Float,
        val hasFeatures: Boolean
    )

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
            val sorted = applySorting(available, sortBy)
            sorted.take(maxResults).map { track ->
                val features = featuresMap[track.id]
                val score = features
                    ?.let { CompositeScoreCalculator.calculate(it, energyCurve.scoreAxis) }
                    ?: CompositeScoreCalculator.DEFAULT_SCORE
                ReplacementCandidate(
                    track = track,
                    compositeScore = score,
                    scoreDifference = 0f,
                    hasFeatures = features != null
                )
            }
        } else {
            available
                .map { track ->
                    val features = featuresMap[track.id]
                    val score = features
                        ?.let { CompositeScoreCalculator.calculate(it, energyCurve.scoreAxis) }
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

    private suspend fun fetchTracks(playlistId: String): List<Track> =
        if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
            repository.getLikedTracks()
        else
            repository.getPlaylistTracks(playlistId)

    private fun applySorting(tracks: List<Track>, option: SortOption): List<Track> =
        when (option) {
            SortOption.POPULARITY -> tracks.sortedByDescending { it.popularity }
            SortOption.DURATION -> tracks.sortedBy { it.durationMs }
            SortOption.RELEASE_DATE -> tracks.sortedByDescending { it.releaseDate ?: "" }
            SortOption.NONE -> tracks
        }
}
