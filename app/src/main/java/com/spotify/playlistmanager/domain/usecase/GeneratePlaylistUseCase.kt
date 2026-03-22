package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.util.EnergyCurveCalculator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use-case generowania playlisty.
 *
 * Przeniesiony z PlaylistGeneratorEngine – teraz zależy od ISpotifyRepository
 * (interfejsu), nie od konkretnej implementacji Androida.
 * Dzięki temu jest testowalny bez emulatora i gotowy na KMP.
 */
@Singleton
class GeneratePlaylistUseCase @Inject constructor(
    private val repository: ISpotifyRepository
) {
    suspend operator fun invoke(sources: List<PlaylistSource>): List<Track> {
        val result = mutableListOf<Track>()
        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue
            val allTracks = if (playlistId == LIKED_SONGS_ID) repository.getLikedTracks()
                            else repository.getPlaylistTracks(playlistId)
            val sorted = if (source.sortBy != SortOption.NONE) applySorting(allTracks, source.sortBy)
                         else allTracks
            val limited = sorted.take(source.trackCount)
            val ordered = if (source.sortBy == SortOption.NONE && source.energyCurve != EnergyCurve.NONE)
                EnergyCurveCalculator.applyEnergyCurve(limited, source.energyCurve)
            else limited
            result.addAll(ordered)
        }
        return result
    }

    private fun applySorting(tracks: List<Track>, option: SortOption): List<Track> =
        when (option) {
            SortOption.POPULARITY   -> tracks.sortedByDescending { it.popularity }
            SortOption.DURATION     -> tracks.sortedBy { it.durationMs }
            SortOption.ENERGY       -> tracks.sortedByDescending { it.energy ?: 0f }
            SortOption.DANCEABILITY -> tracks.sortedByDescending { it.danceability ?: 0f }
            SortOption.TEMPO        -> tracks.sortedByDescending { it.tempo ?: 0f }
            SortOption.RELEASE_DATE -> tracks.sortedByDescending { it.album }
            SortOption.NONE         -> tracks
        }

    companion object {
        const val LIKED_SONGS_ID = "__liked__"
    }
}
