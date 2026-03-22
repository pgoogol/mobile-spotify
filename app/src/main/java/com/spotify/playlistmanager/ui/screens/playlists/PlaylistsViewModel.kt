package com.spotify.playlistmanager.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.repository.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Tryb widoku ───────────────────────────────────────────────────────────────

enum class ViewMode { LIST, GRID }

// ── Opcje sortowania ──────────────────────────────────────────────────────────

enum class PlaylistSortOption(val label: String) {
    DEFAULT("Domyślna"),
    NAME("Nazwa"),
    TRACK_COUNT("Utwory")
}

// ── Stan ekranu ───────────────────────────────────────────────────────────────

sealed class PlaylistsUiState {
    data object Loading : PlaylistsUiState()
    data class  Success(val playlists: List<Playlist>) : PlaylistsUiState()
    data class  Error(val message: String) : PlaylistsUiState()
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: SpotifyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistsUiState>(PlaylistsUiState.Loading)
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(PlaylistSortOption.DEFAULT)
    val sortOption: StateFlow<PlaylistSortOption> = _sortOption.asStateFlow()

    private val _sortReverse = MutableStateFlow(false)
    val sortReverse: StateFlow<Boolean> = _sortReverse.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    /** Przefiltrowane i posortowane playlisty */
    @OptIn(FlowPreview::class)
    val filteredPlaylists: StateFlow<List<Playlist>> =
        combine(
            _searchQuery.debounce(200),
            _uiState,
            _sortOption,
            _sortReverse
        ) { query, state, sort, reverse ->
            if (state !is PlaylistsUiState.Success) return@combine emptyList()

            var list = if (query.isBlank()) state.playlists
            else state.playlists.filter {
                it.name.contains(query, ignoreCase = true) ||
                (it.description?.contains(query, ignoreCase = true) == true)
            }

            list = when (sort) {
                PlaylistSortOption.DEFAULT     -> list
                PlaylistSortOption.NAME        -> list.sortedBy { it.name.lowercase() }
                PlaylistSortOption.TRACK_COUNT -> list.sortedByDescending { it.trackCount }
            }

            if (reverse) list.reversed() else list
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init { loadPlaylists() }

    fun loadPlaylists() {
        _uiState.value = PlaylistsUiState.Loading
        viewModelScope.launch {
            runCatching { repository.getUserPlaylists() }
                .onSuccess { _uiState.value = PlaylistsUiState.Success(it) }
                .onFailure { e ->
                    _uiState.value = PlaylistsUiState.Error(
                        e.message ?: "Błąd pobierania playlist"
                    )
                }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    fun onSortOption(option: PlaylistSortOption) {
        if (_sortOption.value == option) {
            _sortReverse.value = !_sortReverse.value
        } else {
            _sortOption.value  = option
            _sortReverse.value = false
        }
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
    }
}
