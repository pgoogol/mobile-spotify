package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.util.EnergyCurveCalculator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silnik generowania playlist – odpowiednik generat_playlist_gui.py.
 *
 * Algorytm dla każdego segmentu (PlaylistSource):
 *   1. Pobierz wszystkie utwory z playlisty źródłowej
 *   2. Opcjonalnie posortuj wg SortOption
 *   3. Weź pierwsze N utworów
 *   4. Zastosuj krzywą energii (jeśli brak sortowania)
 */
@Singleton
class PlaylistGeneratorEngine @Inject constructor(
    private val repository: SpotifyRepository
) {

    /**
     * Generuje nową playlistę na podstawie listy źródeł.
     * Zwraca gotową listę Track w odpowiedniej kolejności.
     */
    suspend fun generate(sources: List<PlaylistSource>): List<Track> {
        val result = mutableListOf<Track>()

        for (source in sources) {
            val playlistId = source.playlist?.id ?: continue
            val isLiked = playlistId == LIKED_SONGS_ID

            val allTracks = if (isLiked) {
                repository.getLikedTracks()
            } else {
                repository.getPlaylistTracks(playlistId)
            }

            // Sortowanie (wyklucza krzywą energii)
            val sorted = if (source.sortBy != SortOption.NONE) {
                applySorting(allTracks, source.sortBy)
            } else {
                allTracks
            }

            // Ogranicz do żądanej liczby
            val limited = sorted.take(source.trackCount)

            // Krzywa energii (tylko gdy brak sortowania)
            val ordered = if (source.sortBy == SortOption.NONE && source.energyCurve != EnergyCurve.NONE) {
                EnergyCurveCalculator.applyEnergyCurve(limited, source.energyCurve)
            } else {
                limited
            }

            result.addAll(ordered)
        }

        return result
    }

    private fun applySorting(tracks: List<Track>, option: SortOption): List<Track> =
        when (option) {
            SortOption.POPULARITY    -> tracks.sortedByDescending { it.popularity }
            SortOption.DURATION      -> tracks.sortedBy { it.durationMs }
            SortOption.ENERGY        -> tracks.sortedByDescending { it.energy ?: 0f }
            SortOption.DANCEABILITY  -> tracks.sortedByDescending { it.danceability ?: 0f }
            SortOption.TEMPO         -> tracks.sortedByDescending { it.tempo ?: 0f }
            SortOption.RELEASE_DATE  -> tracks.sortedByDescending { it.album }  // approx
            SortOption.NONE          -> tracks
        }

    companion object {
        const val LIKED_SONGS_ID = "__liked__"
    }
}
