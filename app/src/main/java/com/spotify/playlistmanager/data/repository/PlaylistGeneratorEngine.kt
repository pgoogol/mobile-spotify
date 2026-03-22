package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
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
/**
 * Zachowany dla kompatybilności wstecznej (LIKED_SONGS_ID używany w TracksViewModel).
 * Logika generowania przeniesiona do GeneratePlaylistUseCase.
 * @deprecated Używaj GeneratePlaylistUseCase bezpośrednio.
 */
@Singleton
@Deprecated("Use GeneratePlaylistUseCase directly")
class PlaylistGeneratorEngine @Inject constructor(
    private val useCase: GeneratePlaylistUseCase
) {

    suspend fun generate(sources: List<PlaylistSource>): List<Track> = useCase(sources)

    companion object {
        const val LIKED_SONGS_ID = "__liked__"
    }
}
