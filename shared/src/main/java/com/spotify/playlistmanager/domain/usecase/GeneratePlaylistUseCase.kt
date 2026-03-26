package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use-case generowania playlisty.
 *
 * Algorytm dla każdego segmentu (PlaylistSource):
 *  1. Pobierz wszystkie utwory z playlisty źródłowej
 *  2. Opcjonalnie posortuj wg SortOption
 *  3. Weź pierwsze N utworów
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

            val sorted = if (source.sortBy != SortOption.NONE)
                applySorting(allTracks, source.sortBy)
            else allTracks

            result.addAll(sorted.take(source.trackCount))
        }

        return result
    }

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
