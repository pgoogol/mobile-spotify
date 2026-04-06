package com.spotify.playlistmanager.ui.screens.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.PlaylistStats
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.repository.CachePolicy
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
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
    val stats: PlaylistStats? = null,
    /** Epoch ms ostatniego pobrania utworów z cache lub null gdy brak. */
    val tracksFetchedAt: Long? = null
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
    private val featuresRepository: ITrackFeaturesRepository,
    private val playlistCache: IPlaylistCacheRepository
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

    /**
     * Ładuje utwory z cache (jeśli są), a następnie odświeża w tle.
     * Stale-while-revalidate: użytkownik widzi listę natychmiast.
     *
     * @param forceRefresh true = pull-to-refresh, pomija cache
     */
    fun loadTracks(playlistId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                // 1) Spróbuj pokazać cache natychmiast
                val cached = runCatching {
                    if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
                        repository.getLikedTracks(CachePolicy.CACHE_ONLY)
                    else
                        repository.getPlaylistTracks(playlistId, CachePolicy.CACHE_ONLY)
                }.getOrNull().orEmpty()

                if (cached.isNotEmpty()) {
                    _state.value = TracksUiState(
                        tracks = cached,
                        stats = computeStats(cached),
                        tracksFetchedAt = playlistCache.getTracksFetchedAt(playlistId)
                    )
                    loadFeaturesFor(cached)
                    refreshTracksInBackground(playlistId)
                    return@launch
                }
            }

            // Brak cache lub forceRefresh — pokaż loader
            _state.value = TracksUiState(isLoading = true)
            val policy = if (forceRefresh) CachePolicy.NETWORK_ONLY else CachePolicy.CACHE_FIRST
            runCatching {
                if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
                    repository.getLikedTracks(policy)
                else
                    repository.getPlaylistTracks(playlistId, policy)
            }.onSuccess { tracks ->
                _state.value = TracksUiState(
                    tracks = tracks,
                    stats = computeStats(tracks),
                    tracksFetchedAt = playlistCache.getTracksFetchedAt(playlistId)
                )
                loadFeaturesFor(tracks)
            }.onFailure { e ->
                _state.value = TracksUiState(error = e.message ?: "Błąd ładowania")
            }
        }
    }

    private fun refreshTracksInBackground(playlistId: String) {
        viewModelScope.launch {
            runCatching {
                if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
                    repository.getLikedTracks(CachePolicy.CACHE_FIRST)
                else
                    repository.getPlaylistTracks(playlistId, CachePolicy.CACHE_FIRST)
            }.onSuccess { fresh ->
                val current = _state.value.tracks
                if (current != fresh) {
                    _state.value = TracksUiState(
                        tracks = fresh,
                        stats = computeStats(fresh),
                        tracksFetchedAt = playlistCache.getTracksFetchedAt(playlistId)
                    )
                    loadFeaturesFor(fresh)
                } else {
                    // Nawet jeśli dane się nie zmieniły, zaktualizuj timestamp świeżości
                    _state.value = _state.value.copy(
                        tracksFetchedAt = playlistCache.getTracksFetchedAt(playlistId)
                    )
                }
            }
            // Błąd w tle ignorujemy
        }
    }

    private suspend fun loadFeaturesFor(tracks: List<Track>) {
        val trackIds = tracks.mapNotNull { it.id }
        if (trackIds.isNotEmpty()) {
            runCatching { featuresRepository.getFeaturesMap(trackIds) }
                .onSuccess { _featuresMap.value = it }
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