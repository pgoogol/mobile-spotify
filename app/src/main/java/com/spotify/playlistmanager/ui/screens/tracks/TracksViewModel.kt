package com.spotify.playlistmanager.ui.screens.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.PlaylistStats
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TracksUiState(
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
    val stats: PlaylistStats? = null
)

enum class SortColumn(val label: String) {
    TITLE("Tytuł"), ARTIST("Artysta"), ALBUM("Album"),
    DURATION("Czas"), POPULARITY("Popularność"),
    BPM("BPM"), DANCEABILITY("Dance"), ENERGY("Energia")
}

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val repository: ISpotifyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TracksUiState(isLoading = true))
    val state: StateFlow<TracksUiState> = _state.asStateFlow()

    private val _filterQuery   = MutableStateFlow("")
    val filterQuery: StateFlow<String> = _filterQuery.asStateFlow()

    private val _sortColumn  = MutableStateFlow<SortColumn?>(null)
    private val _sortReverse = MutableStateFlow(false)

    val sortColumn:  StateFlow<SortColumn?> = _sortColumn.asStateFlow()
    val sortReverse: StateFlow<Boolean>     = _sortReverse.asStateFlow()

    /** Widoczne wiersze (przefiltrowane + posortowane) */
    @OptIn(FlowPreview::class)
    val visibleTracks: StateFlow<List<Track>> =
        combine(_state, _filterQuery.debounce(200), _sortColumn, _sortReverse) {
                s, query, col, rev ->
            var list = s.tracks
            // filtrowanie
            if (query.isNotBlank()) {
                list = list.filter {
                    it.title.contains(query, true) ||
                    it.artist.contains(query, true) ||
                    it.album.contains(query, true)
                }
            }
            // sortowanie
            if (col != null) {
                val sorted = when (col) {
                    SortColumn.TITLE        -> list.sortedBy { it.title.lowercase() }
                    SortColumn.ARTIST       -> list.sortedBy { it.artist.lowercase() }
                    SortColumn.ALBUM        -> list.sortedBy { it.album.lowercase() }
                    SortColumn.DURATION     -> list.sortedBy { it.durationMs }
                    SortColumn.POPULARITY   -> list.sortedByDescending { it.popularity }
                    SortColumn.BPM          -> list.sortedByDescending { it.tempo ?: 0f }
                    SortColumn.DANCEABILITY -> list.sortedByDescending { it.danceability ?: 0f }
                    SortColumn.ENERGY       -> list.sortedByDescending { it.energy ?: 0f }
                }
                list = if (rev) sorted.reversed() else sorted
            }
            list
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadTracks(playlistId: String) {
        viewModelScope.launch {
            _state.value = TracksUiState(isLoading = true)
            runCatching {
                if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
                    repository.getLikedTracks()
                else
                    repository.getPlaylistTracks(playlistId)
            }.onSuccess { tracks ->
                _state.value = TracksUiState(
                    tracks = tracks,
                    stats  = computeStats(tracks)
                )
            }.onFailure { e ->
                _state.value = TracksUiState(error = e.message ?: "Błąd pobierania")
            }
        }
    }

    fun onFilterChange(q: String) { _filterQuery.value = q }

    fun onSortColumn(col: SortColumn) {
        if (_sortColumn.value == col) {
            _sortReverse.value = !_sortReverse.value
        } else {
            _sortColumn.value  = col
            _sortReverse.value = false
        }
    }

    // ── Statystyki (odpowiednik update_playlist_stats) ─────────────────────
    private fun computeStats(tracks: List<Track>): PlaylistStats {
        val bpms     = tracks.mapNotNull { it.tempo }
        val energies = tracks.mapNotNull { it.energy }
        val dances   = tracks.mapNotNull { it.danceability }
        val total    = tracks.sumOf { it.durationMs.toLong() }
        return PlaylistStats(
            trackCount      = tracks.size,
            totalDurationMs = total,
            avgBpm          = bpms.averageOrNull()?.toFloat(),
            minBpm          = bpms.minOrNull(),
            maxBpm          = bpms.maxOrNull(),
            avgEnergy       = energies.averageOrNull()?.toFloat(),
            avgDanceability = dances.averageOrNull()?.toFloat()
        )
    }

    private fun List<Float>.averageOrNull() =
        if (isEmpty()) null else sum() / size
}
