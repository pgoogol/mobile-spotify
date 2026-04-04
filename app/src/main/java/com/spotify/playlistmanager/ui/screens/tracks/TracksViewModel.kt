package com.spotify.playlistmanager.ui.screens.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.PlaylistStats
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
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
    TITLE("Tytuł"),
    ARTIST("Artysta"),
    ALBUM("Album"),
    DURATION("Czas"),
    POPULARITY("Popularność")
}

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val repository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TracksUiState(isLoading = true))
    val state: StateFlow<TracksUiState> = _state.asStateFlow()

    private val _filterQuery   = MutableStateFlow("")
    val filterQuery: StateFlow<String> = _filterQuery.asStateFlow()

    private val _sortColumn  = MutableStateFlow<SortColumn?>(null)
    private val _sortReverse = MutableStateFlow(false)

    val sortColumn:  StateFlow<SortColumn?> = _sortColumn.asStateFlow()
    val sortReverse: StateFlow<Boolean>     = _sortReverse.asStateFlow()

    /** Mapa audio features (track ID → features) z Room cache. */
    private val _featuresMap = MutableStateFlow<Map<String, TrackAudioFeatures>>(emptyMap())
    val featuresMap: StateFlow<Map<String, TrackAudioFeatures>> = _featuresMap.asStateFlow()

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
                    SortColumn.TITLE      -> list.sortedBy { it.title.lowercase() }
                    SortColumn.ARTIST     -> list.sortedBy { it.artist.lowercase() }
                    SortColumn.ALBUM      -> list.sortedBy { it.album.lowercase() }
                    SortColumn.DURATION   -> list.sortedBy { it.durationMs }
                    SortColumn.POPULARITY -> list.sortedByDescending { it.popularity }
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
                // Ładuj audio features z Room cache (jeśli dostępne)
                val trackIds = tracks.mapNotNull { it.id }
                if (trackIds.isNotEmpty()) {
                    runCatching {
                        featuresRepository.getFeaturesMap(trackIds)
                    }.onSuccess { map ->
                        _featuresMap.value = map
                    }
                }
            }.onFailure { e ->
                _state.value = TracksUiState(error = e.message ?: "Błąd ładowania")
            }
        }
    }

    fun onFilterChanged(query: String) {
        _filterQuery.value = query
    }

    fun onSortToggled(column: SortColumn) {
        if (_sortColumn.value == column) {
            if (_sortReverse.value) {
                // Trzecie kliknięcie — reset
                _sortColumn.value = null
                _sortReverse.value = false
            } else {
                _sortReverse.value = true
            }
        } else {
            _sortColumn.value = column
            _sortReverse.value = false
        }
    }

    private fun computeStats(tracks: List<Track>): PlaylistStats =
        PlaylistStats(
            trackCount      = tracks.size,
            totalDurationMs = tracks.sumOf { it.durationMs.toLong() }
        )
}